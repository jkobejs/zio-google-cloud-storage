package io.github.jkobejs.zio.google.cloud.storage.http

import fs2.Chunk

case class ResumableChunk(chunk: Chunk[Byte], rangeFrom: Long, rangeTo: Long, totalSize: Option[Long])
