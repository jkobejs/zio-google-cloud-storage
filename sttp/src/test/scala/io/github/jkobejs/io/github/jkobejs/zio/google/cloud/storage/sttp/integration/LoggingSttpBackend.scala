package io.github.jkobejs.zio.google.cloud.storage.sttp.integration

import sttp.client.{ Request, Response, SttpBackend }
import sttp.client.monad.MonadError
import sttp.client.ws.WebSocketResponse
import io.odin.zio.consoleLogger
import zio.IO
import io.odin.Logger
import zio.Task
import io.odin.zio.LoggerError
import io.odin.formatter.Formatter

class LoggingSttpBackend[S, WS_HANDLER[_]](delegate: SttpBackend[Task, S, WS_HANDLER])
    extends SttpBackend[Task, S, WS_HANDLER] {
  val logger: Logger[IO[LoggerError, *]] = consoleLogger(formatter = Formatter.colorful)

  override def send[T](request: Request[T, S]): Task[Response[T]] =
    responseMonad.flatMap(responseMonad.handleError(delegate.send(request)) {
      case e: Exception =>
        println(s"Exception when sending request: $request\n${e.getMessage()}")
        responseMonad.error(e)
    }) { response =>
      (if (response.isSuccess) {
         logger.info(s"For request:\n${request.toCurl}\ngot response: $response")
       } else {
         logger.error(s"For request:\n${request.toCurl}\ngot response: $response\nwith body: ${response.body}")
       }).map(_ => response)
    }
  override def openWebsocket[T, WS_RESULT](
    request: Request[T, S],
    handler: WS_HANDLER[WS_RESULT]
  ): Task[WebSocketResponse[WS_RESULT]] =
    responseMonad.map(responseMonad.handleError(delegate.openWebsocket(request, handler)) {
      case e: Exception =>
        println(s"Exception when opening a websocket request: $request\n${e.getMessage()}")
        responseMonad.error(e)
    }) { response =>
      println(s"For ws request:\n${request.toCurl}\n got headers: ${response.headers}")
      response
    }
  override def close(): Task[Unit]             = delegate.close()
  override def responseMonad: MonadError[Task] = delegate.responseMonad
}
