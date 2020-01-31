package io.github.jkobejs.zio.google.cloud.storage.sttp.integration

import sttp.client.{ Request, Response, SttpBackend }
import sttp.client.monad.MonadError
import sttp.client.ws.WebSocketResponse

class LoggingSttpBackend[F[_], S, WS_HANDLER[_]](delegate: SttpBackend[F, S, WS_HANDLER])
    extends SttpBackend[F, S, WS_HANDLER] {

  override def send[T](request: Request[T, S]): F[Response[T]] =
    responseMonad.map(responseMonad.handleError(delegate.send(request)) {
      case e: Exception =>
        println(s"Exception when sending request: $request\n${e.getMessage()}")
        responseMonad.error(e)
    }) { response =>
      if (response.isSuccess) {
        println(s"For request:\n${request.toCurl}\ngot response: $response")
      } else {
        println(s"For request:\n${request.toCurl}\ngot response: $response\nwith body: ${response.body}")
      }
      response
    }
  override def openWebsocket[T, WS_RESULT](
    request: Request[T, S],
    handler: WS_HANDLER[WS_RESULT]
  ): F[WebSocketResponse[WS_RESULT]] =
    responseMonad.map(responseMonad.handleError(delegate.openWebsocket(request, handler)) {
      case e: Exception =>
        println(s"Exception when opening a websocket request: $request\n${e.getMessage()}")
        responseMonad.error(e)
    }) { response =>
      println(s"For ws request:\n${request.toCurl}\n got headers: ${response.headers}")
      response
    }
  override def close(): F[Unit]             = delegate.close()
  override def responseMonad: MonadError[F] = delegate.responseMonad
}
