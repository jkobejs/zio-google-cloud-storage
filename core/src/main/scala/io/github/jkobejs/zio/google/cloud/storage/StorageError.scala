package io.github.jkobejs.zio.google.cloud.storage

import io.github.jkobejs.zio.google.cloud.oauth2.server2server.authenticator.AuthenticatorError
import io.github.jkobejs.zio.google.cloud.storage.http.HttpError

sealed trait StorageError extends RuntimeException

object StorageError {
  final case class StorageAuthenticatorError(error: AuthenticatorError) extends StorageError {
    override def getMessage: String = error.getMessage
  }
  final case class StorageHttpError(error: HttpError) extends StorageError {
    override def getMessage: String = error.getMessage
  }
  final case class ToManyObjectsToCompose(givenSize: Int, maxSize: Int) extends StorageError {
    override def getMessage: String = s"Given source objects size $givenSize is greater than maximum one ($maxSize)."
  }
}
