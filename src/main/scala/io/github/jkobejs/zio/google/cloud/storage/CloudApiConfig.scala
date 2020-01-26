package io.github.jkobejs.zio.google.cloud.storage

import io.github.jkobejs.zio.google.cloud.oauth2.server2server.authenticator.{ AuthApiClaims, AuthApiConfig }

case class CloudApiConfig(
  storageApiConfig: StorageApiConfig,
  authApiConfig: AuthApiConfig,
  authApiClaims: AuthApiClaims
)
