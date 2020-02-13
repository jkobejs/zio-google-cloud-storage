package io.github.jkobejs.io.github.jkobejs.zio.google.cloud.storage.integration

import cats.effect._
import cats.implicits._
import fs2._
import org.http4s.util.CaseInsensitiveString
import org.http4s.client.Client
import org.http4s.Headers
import org.http4s.Message
import org.http4s.MediaType
import org.http4s.Request
import org.http4s.Charset
import org.http4s.Response
import _root_.io.odin.Logger

/**
 * Simple Middleware for Logging All Requests and Responses
 */
object OdinLogger {
  def apply[F[_]: Concurrent](
    logHeaders: Boolean,
    logBody: Boolean,
    redactHeadersWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains,
    logger: Logger[F]
  )(client: Client[F]): Client[F] =
    OdinResponseLogger.apply(logHeaders, logBody, redactHeadersWhen, logger)(
      OdinRequestLogger.apply(logHeaders, logBody, redactHeadersWhen, logger)(
        client
      )
    )

  def logMessage[F[_], A <: Message[F]](message: A)(
    logHeaders: Boolean,
    logBody: Boolean,
    redactHeadersWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains
  )(log: String => F[Unit])(implicit F: Sync[F]): F[Unit] = {
    val charset  = message.charset
    val isBinary = message.contentType.exists(_.mediaType.binary)
    val isJson = message.contentType.exists(
      mT => mT.mediaType == MediaType.application.json || mT.mediaType == MediaType.application.`vnd.hal+json`
    )

    val isText = !isBinary || isJson

    def prelude = message match {
      case Request(method, uri, httpVersion, _, _, _) =>
        s"$httpVersion $method $uri"

      case Response(status, httpVersion, _, _, _) =>
        s"$httpVersion $status"
    }

    val headers =
      if (logHeaders)
        message.headers.redactSensitive(redactHeadersWhen).toList.mkString("Headers(", ", ", ")")
      else ""

    val bodyStream = if (logBody && isText) {
      message.bodyAsText(charset.getOrElse(Charset.`UTF-8`))
    } else if (logBody) {
      message.body
        .map(b => java.lang.Integer.toHexString(b & 0xff))
    } else {
      Stream.empty.covary[F]
    }

    val bodyText = if (logBody) {
      bodyStream.compile.string
        .map(text => s"""body="$text"""")
    } else {
      F.pure("")
    }

    def spaced(x: String): String = if (x.isEmpty) x else s" $x"

    bodyText
      .map(body => s"$prelude${spaced(headers)}${spaced(body)}")
      .flatMap(log)
  }
}
