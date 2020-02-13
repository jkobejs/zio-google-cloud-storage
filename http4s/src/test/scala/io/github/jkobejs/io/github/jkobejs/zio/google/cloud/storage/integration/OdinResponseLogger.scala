package io.github.jkobejs.io.github.jkobejs.zio.google.cloud.storage.integration

import cats.effect._
import cats.effect.concurrent.Ref
import cats.implicits._
import fs2._
import org.http4s.util.CaseInsensitiveString
import org.http4s.client.Client
import org.http4s.Headers
import org.http4s.Response
import _root_.io.odin.Logger

/**
 * Simple middleware for logging responses as they are processed
 */
object OdinResponseLogger {

  def apply[F[_]](
    logHeaders: Boolean,
    logBody: Boolean,
    redactHeadersWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains,
    logger: Logger[F]
  )(client: Client[F])(implicit F: Concurrent[F]): Client[F] = {
    val log = (s: String) => logger.info(s)
    Client { req =>
      client.run(req).flatMap { response =>
        if (!logBody)
          Resource.liftF(
            OdinLogger.logMessage[F, Response[F]](response)(logHeaders, logBody, redactHeadersWhen)(log(_)) *> F
              .delay(response)
          )
        else
          Resource.suspend {
            Ref[F].of(Vector.empty[Chunk[Byte]]).map { vec =>
              Resource.make(
                F.pure(
                  response.copy(
                    body = response.body
                    // Cannot Be Done Asynchronously - Otherwise All Chunks May Not Be Appended Previous to Finalization
                      .observe(_.chunks.flatMap(s => Stream.eval_(vec.update(_ :+ s))))
                  )
                )
              ) { _ =>
                val newBody = Stream
                  .eval(vec.get)
                  .flatMap(v => Stream.emits(v).covary[F])
                  .flatMap(c => Stream.chunk(c).covary[F])

                OdinLogger
                  .logMessage[F, Response[F]](response.withBodyStream(newBody))(logHeaders, logBody, redactHeadersWhen)(
                    log(_)
                  )
                  .attempt
                  .flatMap {
                    case Left(t)   => logger.error("Error logging reponse body", t)
                    case Right(()) => F.unit
                  }
              }
            }
          }
      }
    }
  }
}
