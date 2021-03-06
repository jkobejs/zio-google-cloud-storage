package io.github.jkobejs.zio.google.cloud.storage.sttp.http

import java.nio.ByteBuffer

import io.circe.generic.auto._
import io.circe.parser.decode
import io.github.jkobejs.zio.google.cloud.storage.{ ComposeObject, StorageApiConfig, StorageObject }
import sttp.client.{ HttpError => _, _ }
import sttp.client.circe._
import sttp.model.StatusCode
import zio.{ IO, Task, ZIO }
import sttp.model._
import fs2.Stream
import fs2.Chunk
import fs2.Pipe
import zio.interop.catz._
import io.github.jkobejs.zio.google.cloud.storage.http._
import io.circe.Decoder

trait SttpClient extends HttpClient {
  implicit val sttpBackend: SttpBackend[Task, Stream[Task, ByteBuffer], Nothing]
  override val storageHttpClient: HttpClient.Service[Any] = new HttpClient.Service[Any] {

    override def listBucket(
      storageApiConfig: StorageApiConfig,
      bucket: String,
      prefix: Option[String],
      accessToken: String,
      nextPageToken: Option[String]
    ): ZIO[Any, HttpError, BucketList] = {
      val uri =
        uri"https://${storageApiConfig.host}/storage/${storageApiConfig.version}/b/$bucket/o?prefix=$prefix&pageToken=$nextPageToken"
      basicRequest.auth
        .bearer(accessToken)
        .get(uri)
        .send()
        .flatMap(decodeOrFail[BucketList])
        .refineOrDie { case e: HttpError => e }
    }

    override def getStorageObject(
      storageApiConfig: StorageApiConfig,
      bucket: String,
      path: String,
      accessToken: String
    ): ZIO[Any, HttpError, Option[StorageObject]] = {
      val uri = uri"https://${storageApiConfig.host}/storage/${storageApiConfig.version}/b/$bucket/o/$path"

      basicRequest.auth
        .bearer(accessToken)
        .get(uri)
        .send()
        .flatMap(decodeOrFailOptional[StorageObject])
        .refineOrDie { case e: HttpError => e }
    }

    override def downloadStorageObject(
      storageApiConfig: StorageApiConfig,
      bucket: String,
      path: String,
      accessToken: String
    ): Stream[Task, Byte] = {
      val uri = uri"https://${storageApiConfig.host}/storage/${storageApiConfig.version}/b/$bucket/o/$path?alt=media"

      Stream
        .eval(
          basicRequest.auth
            .bearer(accessToken)
            .get(uri)
            .response(asStream[Stream[Task, ByteBuffer]])
            .send()
        )
        .flatMap(
          response =>
            response.body match {
              case Right(body) =>
                body.mapChunks(chunk => chunk.flatMap(Chunk.ByteBuffer.apply))
              case Left(error) => Stream.eval(Task.fail(HttpError.HttpRequestError(response.statusText, error)))
            }
        )
    }

    override def simpleUploadStorageObject(
      storageApiConfig: StorageApiConfig,
      bucket: String,
      path: String,
      accessToken: String
    ): Pipe[Task, Byte, StorageObject] = body => {
      val uri =
        uri"https://${storageApiConfig.host}/upload/storage/${storageApiConfig.version}/b/$bucket/o?uploadType=media&name=$path"

      Stream.eval[Task, StorageObject](
        basicRequest
          .streamBody(body.chunks.map(chunk => chunk.toByteBuffer))
          .auth
          .bearer(accessToken)
          .contentType("application/octet-stream")
          .post(uri)
          .send()
          .flatMap(decodeOrFail[StorageObject])
      )
    }

    override def multipartUploadStorageObject(
      storageApiConfig: StorageApiConfig,
      bucket: String,
      storageObject: StorageObject,
      accessToken: String
    ): Pipe[Task, Byte, StorageObject] = media => {
      val uri =
        uri"https://${storageApiConfig.host}/upload/storage/${storageApiConfig.version}/b/$bucket/o?uploadType=multipart"

      Stream
        .eval[Task, StorageObject](
          media.compile
            .to(Chunk)
            .flatMap(
              chunk =>
                basicRequest.auth
                  .bearer(accessToken)
                  .streamBody(media.chunks.map(chunk => chunk.toByteBuffer))
                  .multipartBody(
                    multipart("metadata", storageObject).contentType(MediaType.ApplicationJson),
                    multipart("media", chunk.toByteBuffer).contentType(MediaType.ApplicationOctetStream)
                  )
                  .post(uri)
                  .send()
                  .flatMap(decodeOrFail[StorageObject])
            )
        )

    }

    override def composeStorageObjects(
      storageApiConfig: StorageApiConfig,
      bucket: String,
      compose: ComposeObject,
      accessToken: String
    ): ZIO[Any, HttpError, StorageObject] = {
      val uri =
        uri"https://${storageApiConfig.host}/storage/${storageApiConfig.version}/b/$bucket/o/${compose.destination.name}/compose"

      basicRequest.auth
        .bearer(accessToken)
        .body(compose)
        .post(uri)
        .send()
        .flatMap(decodeOrFail[StorageObject])
        .refineOrDie { case e: HttpError => e }
    }

    override def copyStorageObject(
      storageApiConfig: StorageApiConfig,
      bucket: String,
      sourceBucket: String,
      sourceObject: String,
      destinationBucket: String,
      destinationObject: String,
      metadata: Option[StorageObject],
      accessToken: String
    ): ZIO[Any, HttpError, StorageObject] = {
      val uri =
        uri"https://${storageApiConfig.host}/storage/${storageApiConfig.version}/b/$sourceBucket/o/$sourceObject/copyTo/b/$destinationBucket/o/$destinationObject"

      basicRequest.auth
        .bearer(accessToken)
        .body(metadata)
        .post(uri)
        .send()
        .flatMap(decodeOrFail[StorageObject])
        .refineOrDie { case e: HttpError => e }
    }

    override def deleteStorageObject(
      storageApiConfig: StorageApiConfig,
      bucket: String,
      path: String,
      accessToken: String
    ): ZIO[Any, HttpError, Unit] = {
      val uri = uri"https://${storageApiConfig.host}/storage/${storageApiConfig.version}/b/$bucket/o/$path"
      basicRequest.auth
        .bearer(accessToken)
        .delete(uri)
        .send()
        .flatMap(decodeOrFailForDelete)
        .refineOrDie { case e: HttpError => e }
    }

    override def initiateResumableStorageObjectUpload(
      storageApiConfig: StorageApiConfig,
      bucket: String,
      storageObject: StorageObject,
      accessToken: String
    ): ZIO[Any, HttpError, String] = {
      val uri =
        uri"https://${storageApiConfig.host}/upload/storage/${storageApiConfig.version}/b/$bucket/o?uploadType=resumable"

      basicRequest.auth
        .bearer(accessToken)
        .body(storageObject)
        .post(uri)
        .send()
        .flatMap(
          response =>
            response.header("Location") match {
              case Some(location) => IO.succeed(location)
              case None           => IO.fail(HttpError.ResponseParseError("Location header is missing"))
            }
        )
        .refineOrDie { case e: HttpError => e }
    }

    override def uploadResumableChunk(
      storageApiConfig: StorageApiConfig,
      bucket: String,
      uri: String,
      chunk: ResumableChunk
    ): ZIO[Any, HttpError, Option[StorageObject]] = {
      val resumableUri = uri"$uri"

      val contentRangeSize = chunk.totalSize.map(size => s"$size").getOrElse("*")

      basicRequest
        .header(Header.contentLength(chunk.chunk.size.toLong))
        .header(Header.contentType(MediaType.ApplicationOctetStream))
        .header(
          Header
            .notValidated(HeaderNames.ContentRange, s"bytes ${chunk.rangeFrom}-${chunk.rangeTo - 1}/$contentRangeSize")
        )
        .put(resumableUri)
        .body(chunk.chunk.toArray)
        .send()
        .flatMap(
          response =>
            if (response.code == StatusCode.Ok)
              response.body match {
                case Right(value) =>
                  IO.fromEither(decode[StorageObject](value))
                    .map(Some.apply)
                    .mapError(error => HttpError.ResponseParseError(error.getMessage()))
                case Left(error) =>
                  IO.fail(HttpError.ResponseParseError(error))
              }
            else if (response.code.code == 308)
              IO.none
            else
              IO.fail(HttpError.HttpRequestError(response.statusText, response.body.toString()))
        )
        .refineOrDie {
          case e: HttpError => e
        }
    }
  }

  private def decodeOrFail[A: Decoder](
    response: Response[Either[String, String]]
  ) = {
    println(response.body)
    if (response.code == StatusCode.Ok)
      response.body match {
        case Right(value) =>
          IO.fromEither(decode[A](value))
            .mapError(error => HttpError.ResponseParseError(error.getMessage()))
        case Left(error) =>
          IO.fail(HttpError.HttpRequestError(response.statusText, error))
      }
    else
      IO.fail(HttpError.HttpRequestError(response.statusText, response.body.fold(identity, identity)))
  }
  private def decodeOrFailOptional[A: Decoder](
    response: Response[Either[String, String]]
  ) =
    if (response.code == StatusCode.NotFound)
      IO.none
    else
      decodeOrFail(response).map(Some.apply)

  private def decodeOrFailForDelete(response: Response[Either[String, String]]) =
    if (response.code == StatusCode.NotFound)
      IO.unit
    else if (response.code == StatusCode.NoContent)
      IO.unit
    else
      decodeOrFail[Unit](response)

}
