package io.github.jkobejs.zio.google.cloud.storage.http

import zio.Chunk

case class ResumableChunk(chunk: Chunk[Byte], rangeFrom: Long, rangeTo: Long, totalSize: Option[Long])

