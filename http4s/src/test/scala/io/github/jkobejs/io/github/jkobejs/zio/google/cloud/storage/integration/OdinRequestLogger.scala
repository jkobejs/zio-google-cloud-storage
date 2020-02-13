package io.github.jkobejs.io.github.jkobejs.zio.google.cloud.storage.integration

import cats.effect.Concurrent
import cats.effect.concurrent.Ref
import cats.implicits._
import fs2.{ Chunk, Stream }
import org.http4s.util.CaseInsensitiveString
import org.http4s.client.Client
import io.odin.Logger
import org.http4s.Headers
import cats.effect.Resource
import org.http4s.Request

/**
 * Simple middleware for logging requests using Odin library
 */
object OdinRequestLogger {
  def apply[F[_]: Concurrent](
    logHeaders: Boolean,
    logBody: Boolean,
    redactHeadersWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains,
    logger: Logger[F]
  )(client: Client[F]): Client[F] = {
    val log = (s: String) => logger.info(s)

    Client { req =>
      if (!logBody)
        Resource.liftF(
          OdinLogger
            .logMessage[F, Request[F]](req)(logHeaders, logBody, redactHeadersWhen)(log(_))
        ) *> client
          .run(req)
      else
        Resource.suspend {
          Ref[F].of(Vector.empty[Chunk[Byte]]).map { vec =>
            val newBody = Stream
              .eval(vec.get)
              .flatMap(v => Stream.emits(v).covary[F])
              .flatMap(c => Stream.chunk(c).covary[F])

            val changedRequest = req.withBodyStream(
              req.body
              // Cannot Be Done Asynchronously - Otherwise All Chunks May Not Be Appended Previous to Finalization
                .observe(_.chunks.flatMap(s => Stream.eval_(vec.update(_ :+ s))))
                .onFinalizeWeak(
                  OdinLogger.logMessage[F, Request[F]](req.withBodyStream(newBody))(
                    logHeaders,
                    logBody,
                    redactHeadersWhen
                  )(log(_))
                )
            )

            client.run(changedRequest)
          }
        }
    }
  }
}
