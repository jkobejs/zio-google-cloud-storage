package io.github.jkobejs.zio.google.cloud.storage

import io.github.jkobejs.zio.google.cloud.oauth2.server2server.authenticator.{
  AuthResponse,
  Authenticator,
  AuthenticatorError
}
import io.github.jkobejs.zio.google.cloud.storage.StorageError.{
  StorageAuthenticatorError,
  StorageHttpError,
  ToManyObjectsToCompose
}
import io.github.jkobejs.zio.google.cloud.storage.http.{ BucketList, HttpClient }
import zio.clock.Clock
import zio.stream.{ ZSink, ZStream }
import zio.{ Chunk, IO, ZIO }
import io.github.jkobejs.zio.google.cloud.storage.http.ResumableChunk

trait DefaultStorage extends Storage {
  val storageHttpClient: HttpClient.Service[Any]
  val authenticator: Authenticator.Service[Any]
  val clock: Clock.Service[Any]

  override val storage: Storage.Service[Any] = new Storage.Service[Any] {

    override def get(
      cloudApiConfig: CloudApiConfig,
      bucket: String,
      path: String
    ): ZIO[Any, StorageError, Option[StorageObject]] =
      for {
        authResponse <- authenticator
                         .auth(cloudApiConfig.authApiConfig, cloudApiConfig.authApiClaims)
                         .mapError(StorageAuthenticatorError.apply)
        storageObject <- storageHttpClient
                          .getStorageObject(cloudApiConfig.storageApiConfig, bucket, path, authResponse.accessToken)
                          .mapError(StorageHttpError.apply)

      } yield storageObject

    override def delete(cloudApiConfig: CloudApiConfig, bucket: String, path: String): ZIO[Any, StorageError, Unit] =
      for {
        authResponse <- authenticator
                         .auth(cloudApiConfig.authApiConfig, cloudApiConfig.authApiClaims)
                         .mapError(StorageAuthenticatorError.apply)
        result <- storageHttpClient
                   .deleteStorageObject(cloudApiConfig.storageApiConfig, bucket, path, authResponse.accessToken)
                   .mapError(StorageHttpError.apply)
      } yield result

    override def compose(
      cloudApiConfig: CloudApiConfig,
      bucket: String,
      composeObject: ComposeObject
    ): ZIO[Any, StorageError, StorageObject] = {
      val maximumSize = 32
      if (composeObject.sourceObjects.size > maximumSize)
        ZIO.fail(ToManyObjectsToCompose(composeObject.sourceObjects.size, maximumSize))
      else
        for {
          authResponse <- authenticator
                           .auth(cloudApiConfig.authApiConfig, cloudApiConfig.authApiClaims)
                           .mapError(StorageAuthenticatorError.apply)
          storageObject <- storageHttpClient
                            .composeStorageObjects(
                              cloudApiConfig.storageApiConfig,
                              bucket,
                              composeObject,
                              authResponse.accessToken
                            )
                            .mapError(StorageHttpError.apply)

        } yield storageObject
    }

    override def copy(
      cloudApiConfig: CloudApiConfig,
      bucket: String,
      sourceBucket: String,
      sourceObject: String,
      destinationBucket: String,
      destinationObject: String,
      metadata: Option[StorageObject]
    ): ZIO[Any, StorageError, StorageObject] =
      for {
        authResponse <- authenticator
                         .auth(cloudApiConfig.authApiConfig, cloudApiConfig.authApiClaims)
                         .mapError(StorageAuthenticatorError.apply)
        storageObject <- storageHttpClient
                          .copyStorageObject(
                            cloudApiConfig.storageApiConfig,
                            bucket,
                            sourceBucket,
                            sourceObject,
                            destinationBucket,
                            destinationObject,
                            metadata,
                            authResponse.accessToken
                          )
                          .mapError(StorageHttpError.apply)
      } yield storageObject

    override def rewrite(
      cloudApiConfig: CloudApiConfig,
      bucket: String,
      source: String,
      destination: String
    ): ZIO[Any, StorageError, Unit] = ???

    override def list(
      cloudApiConfig: CloudApiConfig,
      bucket: String,
      prefix: Option[String]
    ): ZStream[Any, StorageError, StorageObject] = {
      def getNext(
        cachedAuth: IO[AuthenticatorError, AuthResponse]
      )(token: Option[String]): IO[StorageError, Option[(Seq[StorageObject], Option[String])]] =
        if (token.isDefined)
          fetch(token, cachedAuth).map(list => Some((list.items, list.nextPageToken)))
        else
          IO.succeed(None)

      def fetch(token: Option[String], cachedAuth: IO[AuthenticatorError, AuthResponse]): IO[StorageError, BucketList] =
        for {
          authResponse <- cachedAuth.mapError(StorageAuthenticatorError.apply)
          bucketList <- storageHttpClient
                         .listBucket(cloudApiConfig.storageApiConfig, bucket, prefix, authResponse.accessToken, token)
                         .mapError(StorageHttpError.apply)
        } yield bucketList

      (for {
        cachedAuth <- ZStream.fromEffect(
                       authenticator
                         .auth(cloudApiConfig.authApiConfig, cloudApiConfig.authApiClaims)
                         .cached(cloudApiConfig.authApiClaims.expiresIn)
                     )
        result <- ZStream
                   .fromEffect(fetch(None, cachedAuth))
                   .flatMap(
                     bucketList =>
                       ZStream.fromIterable(bucketList.items) ++ ZStream
                         .unfoldM(bucketList.nextPageToken)(getNext(cachedAuth))
                         .mapConcat(identity)
                   )
      } yield result).provide(Clock.Live)
    }

    override def download(
      cloudApiConfig: CloudApiConfig,
      bucket: String,
      path: String
    ): ZStream[Any, StorageError, Byte] =
      for {
        authResponse <- ZStream.fromEffect(
                         authenticator
                           .auth(cloudApiConfig.authApiConfig, cloudApiConfig.authApiClaims)
                           .mapError(StorageAuthenticatorError.apply)
                       )
        downloaded <- storageHttpClient
                       .downloadStorageObject(cloudApiConfig.storageApiConfig, bucket, path, authResponse.accessToken)
                       .mapError(StorageHttpError.apply)
      } yield downloaded

    override def simpleUpload(
      cloudApiConfig: CloudApiConfig,
      bucket: String,
      body: ZStream[Any, Throwable, Chunk[Byte]],
      path: String
    ): ZIO[Any, StorageError, StorageObject] =
      for {
        authResponse <- authenticator
                         .auth(cloudApiConfig.authApiConfig, cloudApiConfig.authApiClaims)
                         .mapError(StorageAuthenticatorError.apply)
        storageObject <- storageHttpClient
                          .simpleUploadStorageObject(
                            cloudApiConfig.storageApiConfig,
                            bucket,
                            path,
                            body,
                            authResponse.accessToken
                          )
                          .mapError(StorageHttpError.apply)
      } yield storageObject

    override def multipartUpload(
      cloudApiConfig: CloudApiConfig,
      bucket: String,
      storageObject: StorageObject,
      media: ZStream[Any, Throwable, Chunk[Byte]]
    ): ZIO[Any, StorageError, StorageObject] =
      for {
        authResponse <- authenticator
                         .auth(cloudApiConfig.authApiConfig, cloudApiConfig.authApiClaims)
                         .mapError(StorageAuthenticatorError.apply)
        uploaded <- storageHttpClient
                     .multipartUploadStorageObject(
                       cloudApiConfig.storageApiConfig,
                       bucket,
                       storageObject,
                       media,
                       authResponse.accessToken
                     )
                     .mapError(StorageHttpError.apply)
      } yield uploaded

    override def resumableUpload(
      cloudApiConfig: CloudApiConfig,
      bucket: String,
      storageObject: StorageObject,
      chunkMultiple: Int = 1
    ): ZSink[Any, StorageError, Byte, Byte, StorageObject] =
      for {
        authResponse <- ZSink.fromEffect(
                         authenticator
                           .auth(cloudApiConfig.authApiConfig, cloudApiConfig.authApiClaims)
                           .mapError(StorageAuthenticatorError.apply)
                       )
        resumableUri <- ZSink
                         .fromEffect(
                           storageHttpClient.initiateResumableStorageObjectUpload(
                             cloudApiConfig.storageApiConfig,
                             bucket,
                             storageObject,
                             authResponse.accessToken
                           )
                         )
                         .mapError(StorageError.StorageHttpError.apply)
        MAX_CHUNK_SIZE <- ZSink.succeed(chunkMultiple * 256 * 1024)
        (remainder, uploadedSize) <- ZSink.foldM[Any, StorageError, Byte, Byte, (List[Byte], Long)](
                                      (List.empty, 0L)
                                    )(_ => true) {
                                      case ((chunk, uploadedSize), byte) =>
                                        val newChunk = byte :: chunk
                                        if (newChunk.size < 2 * MAX_CHUNK_SIZE)
                                          IO.succeed(((newChunk, newChunk.size.toLong), Chunk.empty))
                                        else {
                                          val (chunkToUpload, remainder) = newChunk.splitAt(MAX_CHUNK_SIZE)
                                          storageHttpClient
                                            .uploadResumableChunk(
                                              cloudApiConfig.storageApiConfig,
                                              bucket,
                                              resumableUri,
                                              ResumableChunk(
                                                chunk = Chunk.fromIterable(chunkToUpload),
                                                rangeFrom = uploadedSize,
                                                rangeTo = uploadedSize + chunkToUpload.size - 1,
                                                totalSize = None
                                              )
                                            )
                                            .map(
                                              _ => ((remainder, uploadedSize + chunkToUpload.size), Chunk.empty)
                                            )
                                            .mapError(StorageError.StorageHttpError.apply)
                                        }
                                    }
        storageObject <- ZSink
                          .fromEffect(
                            storageHttpClient
                              .uploadResumableChunk(
                                cloudApiConfig.storageApiConfig,
                                bucket,
                                resumableUri,
                                ResumableChunk(
                                  chunk = Chunk.fromIterable(remainder),
                                  rangeFrom = uploadedSize,
                                  rangeTo = uploadedSize + remainder.size,
                                  totalSize = Some(uploadedSize + remainder.size)
                                )
                              )
                          )
                          .map(_.get)
                          .mapError(StorageError.StorageHttpError.apply)
      } yield storageObject
  }
}
