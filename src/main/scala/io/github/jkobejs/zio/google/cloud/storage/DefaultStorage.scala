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
import fs2.{ Pipe, Stream }
import zio.{ IO, ZIO }
import io.github.jkobejs.zio.google.cloud.storage.http.ResumableChunk
import zio.RIO
import zio.Task
import fs2.Chunk

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
    ): Stream[RIO[Any, *], StorageObject] = {
      def getNext(
        cachedAuth: IO[AuthenticatorError, AuthResponse]
      )(token: Option[String]): Task[Option[(Chunk[StorageObject], Option[String])]] =
        if (token.isDefined)
          fetch(token, cachedAuth).map(list => Some((Chunk(list.items: _*), list.nextPageToken)))
        else
          IO.succeed(None)

      def fetch(token: Option[String], cachedAuth: IO[AuthenticatorError, AuthResponse]): Task[BucketList] =
        for {
          authResponse <- cachedAuth.mapError(StorageAuthenticatorError.apply)
          bucketList <- storageHttpClient
                         .listBucket(cloudApiConfig.storageApiConfig, bucket, prefix, authResponse.accessToken, token)
                         .mapError(StorageHttpError.apply)
        } yield bucketList

      for {
        cachedAuth <- Stream.eval(
                       authenticator
                         .auth(cloudApiConfig.authApiConfig, cloudApiConfig.authApiClaims)
                         .cached(cloudApiConfig.authApiClaims.expiresIn)
                         .provide(Clock.Live)
                     )
        result <- Stream
                   .eval(fetch(None, cachedAuth))
                   .flatMap(
                     bucketList =>
                       Stream
                         .emits(bucketList.items) ++ Stream
                         .unfoldChunkEval(bucketList.nextPageToken)(getNext(cachedAuth))
                   )
      } yield result
    }

    override def download(
      cloudApiConfig: CloudApiConfig,
      bucket: String,
      path: String
    ): Stream[RIO[Any, *], Byte] =
      for {
        authResponse <- Stream.eval(
                         authenticator
                           .auth(cloudApiConfig.authApiConfig, cloudApiConfig.authApiClaims)
                           .mapError(StorageAuthenticatorError.apply)
                       )
        downloaded <- storageHttpClient
                       .downloadStorageObject(cloudApiConfig.storageApiConfig, bucket, path, authResponse.accessToken)
      } yield downloaded

    override def simpleUpload(
      cloudApiConfig: CloudApiConfig,
      bucket: String,
      path: String
    ): Pipe[RIO[Any, *], Byte, StorageObject] =
      body =>
        for {
          authResponse <- Stream.eval(
                           authenticator
                             .auth(cloudApiConfig.authApiConfig, cloudApiConfig.authApiClaims)
                             .mapError(StorageAuthenticatorError.apply)
                         )
          storageObject <- storageHttpClient
                            .simpleUploadStorageObject(
                              cloudApiConfig.storageApiConfig,
                              bucket,
                              path,
                              authResponse.accessToken
                            )(body)
        } yield storageObject

    override def multipartUpload(
      cloudApiConfig: CloudApiConfig,
      bucket: String,
      storageObject: StorageObject
    ): Pipe[RIO[Any, *], Byte, StorageObject] =
      media =>
        for {
          authResponse <- Stream.eval(
                           authenticator
                             .auth(cloudApiConfig.authApiConfig, cloudApiConfig.authApiClaims)
                             .mapError(StorageAuthenticatorError.apply)
                         )
          uploaded <- storageHttpClient
                       .multipartUploadStorageObject(
                         cloudApiConfig.storageApiConfig,
                         bucket,
                         storageObject,
                         authResponse.accessToken
                       )(media)
        } yield uploaded

    override def resumableUpload(
      cloudApiConfig: CloudApiConfig,
      bucket: String,
      storageObject: StorageObject,
      chunkMultiple: Int = 1
    ): Pipe[Task, Byte, StorageObject] =
      body =>
        for {
          authResponse <- Stream.eval(
                           authenticator
                             .auth(cloudApiConfig.authApiConfig, cloudApiConfig.authApiClaims)
                             .mapError(StorageAuthenticatorError.apply)
                         )
          resumableUri <- Stream
                           .eval(
                             storageHttpClient
                               .initiateResumableStorageObjectUpload(
                                 cloudApiConfig.storageApiConfig,
                                 bucket,
                                 storageObject,
                                 authResponse.accessToken
                               )
                               .mapError(StorageError.StorageHttpError.apply)
                           )
          chunkSize = 256 * 1024 * chunkMultiple
          storageObject <- body
                            .chunkN(chunkSize)
                            .through(chunkWithTotalSizePipe)
                            .evalMap(
                              chunkWithTotalSize =>
                                storageHttpClient.uploadResumableChunk(
                                  cloudApiConfig.storageApiConfig,
                                  bucket,
                                  resumableUri,
                                  chunkWithTotalSize
                                )
                            )
                            .collectFirst { case Some(storageObject) => storageObject }
        } yield storageObject
  }

  private def chunkWithTotalSizePipe: Pipe[Task, Chunk[Byte], ResumableChunk] = {

    /**
     * Delays output of chunk for one place so that when pull is complete chunk with stream total size is
     */
    def go(
      stream: Stream[Task, Chunk[Byte]],
      chunkToPush: Chunk[Byte],
      size: Long
    ): fs2.Pull[Task, ResumableChunk, Unit] =
      stream.pull.uncons1.flatMap {
        case Some((head, tail)) =>
          if (chunkToPush.isEmpty)
            go(tail, head, size)
          else
            fs2.Pull.output1(ResumableChunk(chunkToPush, size, size + chunkToPush.size, None)) >> go(
              tail,
              head,
              size + head.size
            )
        case None =>
          fs2.Pull.output1(
            ResumableChunk(chunkToPush, size, size + chunkToPush.size, Some(size + chunkToPush.size))
          ) >> fs2.Pull.done
      }

    in => go(in, Chunk.empty[Byte], 0).stream
  }
}
