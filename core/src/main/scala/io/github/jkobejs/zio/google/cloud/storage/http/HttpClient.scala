package io.github.jkobejs.zio.google.cloud.storage.http

import io.github.jkobejs.zio.google.cloud.storage.{ ComposeObject, StorageApiConfig, StorageObject }
import zio._
import fs2.Stream
import fs2.Pipe

trait HttpClient {
  val storageHttpClient: HttpClient.Service[Any]
}

object HttpClient {
  trait Service[R] {
    def listBucket(
      storageApiConfig: StorageApiConfig,
      bucket: String,
      prefix: Option[String],
      accessToken: String,
      nextPageToken: Option[String]
    ): ZIO[R, HttpError, BucketList]
    def getStorageObject(
      storageApiConfig: StorageApiConfig,
      bucket: String,
      path: String,
      accessToken: String
    ): ZIO[R, HttpError, Option[StorageObject]]
    def downloadStorageObject(
      storageApiConfig: StorageApiConfig,
      bucket: String,
      path: String,
      accessToken: String
    ): Stream[RIO[R, *], Byte]
    def simpleUploadStorageObject(
      storageApiConfig: StorageApiConfig,
      bucket: String,
      path: String,
      accessToken: String
    ): Pipe[RIO[R, *], Byte, StorageObject]
    def multipartUploadStorageObject(
      storageApiConfig: StorageApiConfig,
      bucket: String,
      storageObject: StorageObject,
      accessToken: String
    ): Pipe[RIO[R, *], Byte, StorageObject]
    def composeStorageObjects(
      storageApiConfig: StorageApiConfig,
      bucket: String,
      compose: ComposeObject,
      accessToken: String
    ): ZIO[R, HttpError, StorageObject]
    def copyStorageObject(
      storageApiConfig: StorageApiConfig,
      bucket: String,
      sourceBucket: String,
      sourceObject: String,
      destinationBucket: String,
      destinationObject: String,
      metadata: Option[StorageObject],
      accessToken: String
    ): ZIO[R, HttpError, StorageObject]
    def deleteStorageObject(
      storageApiConfig: StorageApiConfig,
      bucket: String,
      path: String,
      accessToken: String
    ): ZIO[R, HttpError, Unit]
    def initiateResumableStorageObjectUpload(
      storageApiConfig: StorageApiConfig,
      bucket: String,
      storageObject: StorageObject,
      accessToken: String
    ): ZIO[R, HttpError, String]
    def uploadResumableChunk(
      storageApiConfig: StorageApiConfig,
      bucket: String,
      uri: String,
      chunk: ResumableChunk
    ): ZIO[R, HttpError, Option[StorageObject]]
  }
}
