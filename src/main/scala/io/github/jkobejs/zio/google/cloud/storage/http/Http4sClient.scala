package io.github.jkobejs.zio.google.cloud.storage.http

import java.nio.ByteBuffer

import cats.MonadError
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.auto._
import io.github.jkobejs.zio.google.cloud.storage.{ ComposeObject, StorageApiConfig, StorageObject }
import org.http4s._
import org.http4s.circe.{ jsonEncoderOf, jsonOf }
import org.http4s.client.Client
import sttp.client.SttpBackend
import zio.interop.catz._
import zio.stream.{ ZSink, ZStream }
import zio.{ Chunk, RIO, Task, ZIO }

trait Http4sClient extends HttpClient {
  type ZioSttpBackend = SttpBackend[Task, ZStream[Any, Throwable, ByteBuffer], Nothing]
  val client: Client[Task]

  override val storageHttpClient: HttpClient.Service[Any] = new HttpClient.Service[Any] {
    implicit private val customConfig: Configuration                              = Configuration.default.withDefaults
    implicit private val bucketListDecoder: EntityDecoder[Task, BucketList]       = jsonOf[Task, BucketList]
    implicit private val storageObjectDecoder: EntityDecoder[Task, StorageObject] = jsonOf[Task, StorageObject]
    implicit private val composeObjectEntityEncoder: EntityEncoder[Task, ComposeObject] =
      jsonEncoderOf[Task, ComposeObject]

    override def listBucket(
      storageApiConfig: StorageApiConfig,
      bucket: String,
      prefix: Option[String],
      accessToken: String,
      nextPageToken: Option[String]
    ): ZIO[Any, HttpError, BucketList] =
      (for {
        uri <- parseUri(
                s"https://${storageApiConfig.host}/storage/${storageApiConfig.version}/b/$bucket/o"
              )
        request = Request[Task](
          method = Method.GET,
          uri = uri
            .withQueryParam("prefix", prefix.getOrElse(""))
            .withQueryParam("maxResults", "1")
            .withOptionQueryParam("pageToken", nextPageToken)
        ).withHeaders(headers.Authorization(Credentials.Token(AuthScheme.Bearer, accessToken)))
        response <- client.fetch[BucketList](request)(decodeOrFail[BucketList])
      } yield response).refineOrDie { case e: HttpError => e }

    override def getStorageObject(
      storageApiConfig: StorageApiConfig,
      bucket: String,
      path: String,
      accessToken: String
    ): ZIO[Any, HttpError, Option[StorageObject]] =
      (for {
        uri <- parseUri(
                s"https://${storageApiConfig.host}/storage/${storageApiConfig.version}/b/$bucket/o"
              )
        request = Request[Task](
          method = Method.GET,
          uri = uri./(path)
        ).withHeaders(headers.Authorization(Credentials.Token(AuthScheme.Bearer, accessToken)))
        response <- client.fetch[StorageObject](request)(decodeOrFail[StorageObject])
      } yield Some(response)).refineOrDie { case e: HttpError => e }

    override def downloadStorageObject(
      storageApiConfig: StorageApiConfig,
      bucket: String,
      path: String,
      accessToken: String
    ): ZStream[Any, HttpError, Byte] = ???

    override def simpleUploadStorageObject(
      storageApiConfig: StorageApiConfig,
      bucket: String,
      path: String,
      body: ZStream[Any, Throwable, Chunk[Byte]],
      accessToken: String
    ): ZIO[Any, HttpError, StorageObject] = {

      def upload(body: fs2.Chunk[Byte]) =
        (for {
          uri <- parseUri(s"https://${storageApiConfig.host}/upload/storage/${storageApiConfig.version}/b/$bucket/o")
          request = Request[Task](
            method = Method.POST,
            uri = uri
              .withQueryParam("uploadType", "media")
              .withQueryParam("name", path),
            body = fs2.Stream.chunk(body)
          ).withHeaders(
            headers.Authorization(Credentials.Token(AuthScheme.Bearer, accessToken)),
            headers.`Content-Type`(MediaType.application.`octet-stream`, Charset.`UTF-8`)
          )
          _             <- client.fetch[Unit](request)(decodeOrFail[Unit])
          storageObject <- getStorageObject(storageApiConfig, bucket, path, accessToken)
        } yield storageObject).refineOrDie { case e: HttpError => e }

      null
    }

    override def multipartUploadStorageObject(
      storageApiConfig: StorageApiConfig,
      bucket: String,
      storageObject: StorageObject,
      media: ZStream[Any, Throwable, Chunk[Byte]],
      accessToken: String
    ): ZIO[Any, HttpError, StorageObject] =
      ???

    override def composeStorageObjects(
      storageApiConfig: StorageApiConfig,
      bucket: String,
      compose: ComposeObject,
      accessToken: String
    ): ZIO[Any, HttpError, StorageObject] =
      (for {
        uri <- parseUri(
                s"https://${storageApiConfig.host}/storage/${storageApiConfig.version}/b/$bucket/o"
              )
        request = Request[Task](
          method = Method.POST,
          uri = uri./(s"${compose.destination.name}") / "compose"
        ).withHeaders(headers.Authorization(Credentials.Token(AuthScheme.Bearer, accessToken)))
          .withEntity(compose)
        storageObject <- client.fetch[StorageObject](request)(decodeOrFail[StorageObject])
      } yield storageObject).refineOrDie { case e: HttpError => e }

    override def copyStorageObject(
      storageApiConfig: StorageApiConfig,
      bucket: String,
      sourceBucket: String,
      sourceObject: String,
      destinationBucket: String,
      destinationObject: String,
      metadata: Option[StorageObject],
      accessToken: String
    ): ZIO[Any, HttpError, StorageObject] = ???

    override def deleteStorageObject(
      storageApiConfig: StorageApiConfig,
      bucket: String,
      path: String,
      accessToken: String
    ): ZIO[Any, HttpError, Unit] =
      (for {
        uri <- parseUri(
                s"https://${storageApiConfig.host}/storage/${storageApiConfig.version}/b/$bucket/o"
              )
        request = Request[Task](
          method = Method.DELETE,
          uri = uri./(path)
        ).withHeaders(headers.Authorization(Credentials.Token(AuthScheme.Bearer, accessToken)))
        result <- client.fetch[Unit](request)(decodeOrFail[Unit])
      } yield result).refineOrDie { case e: HttpError => e }

    override def initiateResumableStorageObjectUpload(
      storageApiConfig: StorageApiConfig,
      bucket: String,
      storageObject: StorageObject,
      accessToken: String
    ): ZIO[Any, HttpError, String] = ???

    override def uploadResumableChunk(
      storageApiConfig: StorageApiConfig,
      bucket: String,
      uri: String,
      chunk: ResumableChunk
    ): ZIO[Any, HttpError, Option[StorageObject]] = ???

    private def parseUri(uri: String) =
      Task.fromTry(Uri.fromString(uri).toTry).refineOrDie {
        case error: ParseFailure => HttpError.UriParseError(error.sanitized)
      }

    private def decodeOrFail[A](
      response: Response[Task]
    )(implicit F: MonadError[Task, Throwable], decoder: EntityDecoder[Task, A]) =
      if (response.status.isSuccess)
        response
          .as[A](F, decoder)
          .refineOrDie {
            case failure: DecodeFailure => HttpError.ResponseParseError(failure.getMessage)
          }
      else
        for {
          responseBody <- response.as[String](monadErrorInstance, EntityDecoder.text[Task])
          result <- Task.fail(
                     HttpError.HttpRequestError(
                       status = response.status.toString(),
                       body = responseBody
                     )
                   )
        } yield result
  }
}
