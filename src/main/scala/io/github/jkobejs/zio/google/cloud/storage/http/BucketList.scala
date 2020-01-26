package io.github.jkobejs.zio.google.cloud.storage.http

import io.github.jkobejs.zio.google.cloud.storage.StorageObject

case class BucketList(kind: String, nextPageToken: Option[String] = None, items: List[StorageObject] = Nil)
