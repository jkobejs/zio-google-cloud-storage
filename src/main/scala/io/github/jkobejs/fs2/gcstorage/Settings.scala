package io.github.jkobejs.fs2.storage

import io.github.jkobejs.google.oauth4s.ServerToServer

case class Settings(
  bucket: String,
  oauthSettings: ServerToServer.Settings,
  host: String = "www.googleapis.com",
  version: String = "v1"
)
