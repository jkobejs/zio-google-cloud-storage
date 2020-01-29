package io.github.jkobejs.zio.google.cloud.storage.http

import cats.MonadError
import io.circe.generic.extras.Configuration
import io.github.jkobejs.zio.google.cloud.storage.{ ComposeObject, StorageApiConfig, StorageObject }
import org.http4s._
import org.http4s.circe.{ jsonEncoderOf, jsonOf }
import io.circe.generic.extras.auto._
import io.circe.syntax._
import org.http4s.circe._
import org.http4s.client.Client
import zio.interop.catz._
import zio.{ Task, ZIO }
import fs2.{ Pipe, Stream }
import org.http4s.multipart.Multipart
import org.http4s.multipart.Part

trait Http4sClient extends HttpClient {
  val client: Client[Task]

  override val storageHttpClient: HttpClient.Service[Any] = new HttpClient.Service[Any] {
    implicit private val customConfig: Configuration                              = Configuration.default.withDefaults
    implicit private val bucketListDecoder: EntityDecoder[Task, BucketList]       = jsonOf[Task, BucketList]
    implicit private val storageObjectDecoder: EntityDecoder[Task, StorageObject] = jsonOf[Task, StorageObject]
    implicit private val composeObjectEntityEncoder: EntityEncoder[Task, ComposeObject] =
      jsonEncoderOf[Task, ComposeObject]
    implicit private val storageObjectEntityEncoder: EntityEncoder[Task, StorageObject] =
      jsonEncoderOf[Task, StorageObject]

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
        response <- client.fetch[Option[StorageObject]](request)(decodeOrFailOptional[StorageObject])
      } yield response).refineOrDie { case e: HttpError => e }

    override def downloadStorageObject(
      storageApiConfig: StorageApiConfig,
      bucket: String,
      path: String,
      accessToken: String
    ): Stream[Task, Byte] =
      for {
        uri <- Stream.eval(
                parseUri(
                  s"https://${storageApiConfig.host}/storage/${storageApiConfig.version}/b/$bucket/o"
                )
              )
        request = Request[Task](
          method = Method.GET,
          uri = uri./(path).withQueryParam("alt", "media")
        ).withHeaders(headers.Authorization(Credentials.Token(AuthScheme.Bearer, accessToken)))
        body <- client.stream(request).flatMap(_.body)
      } yield body

    override def simpleUploadStorageObject(
      storageApiConfig: StorageApiConfig,
      bucket: String,
      path: String,
      accessToken: String
    ): Pipe[Task, Byte, StorageObject] = body => {
      Stream.eval(for {
        uri <- parseUri(s"https://${storageApiConfig.host}/upload/storage/${storageApiConfig.version}/b/$bucket/o")
        request = Request[Task](
          method = Method.POST,
          uri = uri
            .withQueryParam("uploadType", "media")
            .withQueryParam("name", path),
          body = body
        ).withHeaders(
          headers.Authorization(Credentials.Token(AuthScheme.Bearer, accessToken)),
          headers.`Content-Type`(MediaType.application.`octet-stream`, Charset.`UTF-8`)
        )
        storageObject <- client.fetch[StorageObject](request)(decodeOrFail[StorageObject])
      } yield storageObject)
    }

    override def multipartUploadStorageObject(
      storageApiConfig: StorageApiConfig,
      bucket: String,
      storageObject: StorageObject,
      accessToken: String
    ): Pipe[Task, Byte, StorageObject] =
      media => {
        val multipart = Multipart[Task](
          Vector(
            Part.formData(
              "metadata",
              storageObject.asJson.toString,
              headers.`Content-Type`(MediaType.application.json, Charset.`UTF-8`)
            ),
            Part.fileData(
              "media",
              storageObject.name,
              media,
              headers.`Content-Type`(MediaType.application.`octet-stream`, Charset.`UTF-8`)
            )
          )
        )

        val entity = EntityEncoder[Task, Multipart[Task]].toEntity(multipart)

        Stream.eval(for {
          uri <- parseUri(s"https://${storageApiConfig.host}/upload/storage/${storageApiConfig.version}/b/$bucket/o")
          request = Request[Task](
            method = Method.POST,
            uri = uri
              .withQueryParam("uploadType", "multipart"),
            body = entity.body
          ).withHeaders(
            headers.Authorization(Credentials.Token(AuthScheme.Bearer, accessToken)),
            headers.`Content-Type`(MediaType.multipartType("related", Some(multipart.boundary.value)))
          )
          storageObject <- client.fetch[StorageObject](request)(decodeOrFail[StorageObject])
        } yield storageObject)
      }

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
        result <- client.fetch[Unit](request)(decodeOrFailForDelete)
      } yield result).refineOrDie { case e: HttpError => e }

    override def initiateResumableStorageObjectUpload(
      storageApiConfig: StorageApiConfig,
      bucket: String,
      storageObject: StorageObject,
      accessToken: String
    ): ZIO[Any, HttpError, String] =
      (for {
        uri <- parseUri(s"https://${storageApiConfig.host}/upload/storage/${storageApiConfig.version}/b/$bucket/o")
        request = Request[Task](
          method = Method.POST,
          uri = uri.withQueryParam("uploadType", "resumable")
        ).withHeaders(headers.Authorization(Credentials.Token(AuthScheme.Bearer, accessToken)))
          .withEntity(
            storageObject
          )
        resumableUri <- client.fetch(request)(extractResumableUri)
      } yield resumableUri.renderString).refineOrDie { case e: HttpError => e }

    override def uploadResumableChunk(
      storageApiConfig: StorageApiConfig,
      bucket: String,
      uri: String,
      chunk: ResumableChunk
    ): ZIO[Any, HttpError, Option[StorageObject]] =
      (for {
        resumableUri <- parseUri(uri)
        request = Request[Task](
          method = Method.PUT,
          uri = resumableUri,
          body = Stream.chunk(chunk.chunk)
        ).withHeaders(
          headers.`Content-Length`.unsafeFromLong(chunk.chunk.size.toLong),
          headers.`Content-Range`(
            headers.Range.SubRange(chunk.rangeFrom, chunk.rangeTo - 1),
            chunk.totalSize
          ),
          headers.`Content-Type`(MediaType.application.`octet-stream`, Charset.`UTF-8`)
        )
        storageObjectOpt <- client.fetch[Option[StorageObject]](request)(decodeOrFailResumable)
      } yield storageObjectOpt).refineOrDie { case e: HttpError => e }

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

    private def decodeOrFailOptional[A](
      response: Response[Task]
    )(implicit F: MonadError[Task, Throwable], decoder: EntityDecoder[Task, A]) =
      if (response.status == Status.NotFound)
        Task.none
      else
        decodeOrFail(response)(F, decoder).map(Some.apply)

    private def decodeOrFailForDelete(response: Response[Task]) =
      if (response.status == Status.NotFound)
        Task.unit
      else
        decodeOrFail[Unit](response)

    private def extractResumableUri(
      response: Response[Task]
    ): Task[Uri] =
      Task.fromEither(
        response.headers.collectFirst { case headers.Location(location) => location.uri }
          .toRight(HttpError.ResponseParseError("Resumable uri is missing"))
      )

    private def decodeOrFailResumable(response: Response[Task]) =
      if (response.status == Status(308, "Resume Incomplete"))
        Task.none
      else
        decodeOrFail[StorageObject](response).map(Some.apply)
  }
}

object Http4sClient {
  def apply(clientT: Client[Task]): HttpClient = new Http4sClient {
    override val client = clientT
  }
}
