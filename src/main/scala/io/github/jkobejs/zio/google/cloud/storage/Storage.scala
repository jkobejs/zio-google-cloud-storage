package io.github.jkobejs.zio.google.cloud.storage

import zio._
import zio.stream._

trait Storage {
  val storage: Storage.Service[Any]
}

object Storage {

  trait Service[R] {
    def compose(
      cloudApiConfig: CloudApiConfig,
      bucket: String,
      composeRequest: ComposeObject
    ): ZIO[R, StorageError, StorageObject]
    def copy(
      cloudApiConfig: CloudApiConfig,
      bucket: String,
      sourceBucket: String,
      sourceObject: String,
      destinationBucket: String,
      destinationObject: String,
      metadata: Option[StorageObject]
    ): ZIO[R, StorageError, StorageObject]
    def delete(cloudApiConfig: CloudApiConfig, bucket: String, path: String): ZIO[R, StorageError, Unit]
    def get(cloudApiConfig: CloudApiConfig, bucket: String, path: String): ZIO[R, StorageError, Option[StorageObject]]
    def rewrite(
      cloudApiConfig: CloudApiConfig,
      bucket: String,
      source: String,
      destination: String
    ): ZIO[R, StorageError, Unit]

    def list(
      cloudApiConfig: CloudApiConfig,
      bucket: String,
      prefix: Option[String]
    ): ZStream[R, StorageError, StorageObject]
    def download(cloudApiConfig: CloudApiConfig, bucket: String, path: String): ZStream[R, StorageError, Byte]
    def simpleUpload(
      cloudApiConfig: CloudApiConfig,
      bucket: String,
      body: ZStream[R, Throwable, Chunk[Byte]],
      path: String
    ): ZIO[R, StorageError, StorageObject]
    def multipartUpload(
      cloudApiConfig: CloudApiConfig,
      bucket: String,
      storageObject: StorageObject,
      media: ZStream[R, Throwable, Chunk[Byte]]
    ): ZIO[R, StorageError, StorageObject]
    def resumableUpload(
      cloudApiConfig: CloudApiConfig,
      bucket: String,
      storageObject: StorageObject,
      chunkMultiple: Int = 1
    ): ZSink[Any, StorageError, Byte, Byte, StorageObject]
  }
}
