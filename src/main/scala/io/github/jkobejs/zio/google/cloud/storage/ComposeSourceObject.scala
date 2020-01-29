package io.github.jkobejs.zio.google.cloud.storage

case class ComposeSourceObject(
  name: String,
  generation: Option[Long] = None,
  objectPreconditions: Option[ObjectPreconditions] = None
)
