package io.github.jkobejs.fs2.storage

import fs2.{Pipe, Stream}
import io.github.jkobejs.google.oauth4s.ServerToServer
import cats.effect.ConcurrentEffect
import org.http4s._
import cats.implicits._
import org.http4s.client.Client
import org.http4s.circe._
import org.http4s.multipart._
import io.circe.syntax._
import io.circe.generic.extras.auto._
import io.circe.generic.extras.Configuration
import fs2.Chunk

import org.http4s._
import org.http4s.circe._
import retry._
import scala.concurrent.duration._

class GCStorage[F[_]: ConcurrentEffect: Sleep](settings: Settings, client: Client[F]) extends Storage[F] {
  type Path          = String
  type StorageObject = GCStorage.StorageObject

  val F = implicitly[ConcurrentEffect[F]]

  private val retryTenTimes                              = RetryPolicies.limitRetries[F](10)
  private val retryWithJitter                            = RetryPolicies.fullJitter[F](1.seconds)
  private val encoding                                   = Charset.`UTF-8`.toString()
  private val serverToServer                             = ServerToServer(client)
  implicit val customConfig: Configuration               = Configuration.default.withDefaults
  implicit private val storageObjectEntityDecoder        = jsonOf[F, StorageObject]
  implicit private val listBucketResponseEntityDecoder   = jsonOf[F, GCStorage.ListBucketResponse]
  implicit private val storageObjectEntityEncoder        = jsonEncoderOf[F, StorageObject]
  implicit private val composeObjectRequestEntityEncoder = jsonEncoderOf[F, GCStorage.ComposeObjectRequest]

  def list(path: Path): Stream[F, StorageObject] = {
    def getNext(token: Option[String]): F[Option[(Chunk[StorageObject], Option[String])]] =
      if (token.isDefined)
        performRequest(token).map(response => Some((Chunk(response.items: _*), response.nextPageToken)))
      else F.pure(None)

    def performRequest(token: Option[String]): F[GCStorage.ListBucketResponse] =
      for {
        authResponse <- serverToServer.auth(settings.oauthSettings)
        request      <- listBucketRequest(path, authResponse.access_token, token)
        response     <- client.expect[GCStorage.ListBucketResponse](request)
      } yield response

    Stream
      .eval(performRequest(None))
      .flatMap(
        response => Stream.emits(response.items) ++ Stream.unfoldChunkEval(response.nextPageToken)(getNext)
      )
  }

  def get(path: Path): F[StorageObject] =
    for {
      authResponse <- serverToServer.auth(settings.oauthSettings)
      request      <- getObjectRequest(java.net.URLEncoder.encode(path, encoding), authResponse.access_token)
      response     <- client.expect[StorageObject](request)
    } yield response

  def download(path: Path): Stream[F, Byte] =
    for {
      authResponse <- Stream.eval(serverToServer.auth(settings.oauthSettings))
      request <- Stream.eval(
                  downloadObjectRequest(java.net.URLEncoder.encode(path, encoding), authResponse.access_token)
                )
      response <- client.stream(request).flatMap(_.body)
    } yield response

  def put(path: Path): Pipe[F, Byte, Unit] = body => {
    for {
      authResponse <- Stream.eval(serverToServer.auth(settings.oauthSettings))
      request <- Stream.eval(
                  simpleUploadRequest(
                    java.net.URLEncoder.encode(path, encoding),
                    authResponse.access_token,
                    body,
                    headers.`Content-Type`(MediaType.application.`octet-stream`, Charset.`UTF-8`)
                  )
                )
      response <- Stream.eval(client.expect[Unit](request))
    } yield response
  }

//  def update(path: Path, storageObject: StorageObject): F[StorageObject] =
//    F.point(storageObject)

  def multipartUpload(storageObject: StorageObject): Pipe[F, Byte, StorageObject] = body => {
    for {
      authResponse <- Stream.eval(serverToServer.auth(settings.oauthSettings))
      request <- Stream.eval(
                  multipartUploadRequest(
                    authResponse.access_token,
                    storageObject,
                    body,
                    headers.`Content-Type`(MediaType.application.`octet-stream`, Charset.`UTF-8`)
                  )
                )
      response <- Stream.eval(client.expect[StorageObject](request))
    } yield response
  }

  /**
   * Use this option if:
   *  - You are transferring large files.
   *  - The likelihood of a network interruption or some other transmission failure is high
   *    (for example, if you are uploading a file from a mobile app).
   */
  def resumableUpload(storageObject: StorageObject, chunkMultiple: Int): Pipe[F, Byte, StorageObject] = body => {
    def backoffRetryCheck(throwable: Throwable): Boolean = throwable match {
      case _: GCStorage.ResumableUploadServerError     => true
      case _: GCStorage.ResumableUploadTooManyRequests => true
      case _                                           => false
    }

    def retryImmediatelyCheck(throwable: Throwable): Boolean = throwable match {
      case _: GCStorage.ResumableUploadRegularError => true
      case _                                        => false
    }

    def onError(throwable: Throwable, details: RetryDetails): F[Unit] =
      F.delay(println(s"$throwable\n$details"))

    for {
      authResponse <- Stream.eval(serverToServer.auth(settings.oauthSettings))
      request      <- Stream.eval(initiateResumableUploadRequest(authResponse.access_token, storageObject))
      resumableUri <- Stream.eval(client.fetch(request)(extractResumableUri))
      chunkSize    = 256 * 1024 * chunkMultiple
      result <- body
                 .chunkN(chunkSize)
                 .through(chunkWithTotalSizePipe)
                 .evalMap(
                   chunkWithTotalSize =>
                     uploadResumableChunk(resumableUri, chunkWithTotalSize).handleErrorWith {
                       case exception: GCStorage.ResumableUploadNotFound =>
                         F.raiseError(exception)
                       case _: GCStorage.GCStorageException =>
                         retryingOnSomeErrors(
                           policy = retryTenTimes,
                           isWorthRetrying = retryImmediatelyCheck,
                           onError = onError
                         )(
                           retryingOnSomeErrors[Option[StorageObject]](
                             policy = retryWithJitter.join(retryTenTimes),
                             isWorthRetrying = backoffRetryCheck,
                             onError = onError
                           )(reUploadResumableChunk(resumableUri, chunkWithTotalSize))
                         )
                       case exception =>
                         F.raiseError(exception)

                   }
                 )
                 .collectFirst { case Some(storageObject) => storageObject }
    } yield result
  }

  def move(source: Path, destination: Path): F[Unit] = ???

  def remove(path: Path): F[Unit] =
    for {
      authResponse <- serverToServer.auth(settings.oauthSettings)
      request      <- removeRequest(java.net.URLEncoder.encode(path, encoding), authResponse.access_token)
      response     <- client.expect[Unit](request)
    } yield response

  def compose(composeRequest: GCStorage.ComposeObjectRequest): F[StorageObject] =
    if (composeRequest.sourceObjects.size > 32)
      F.raiseError(GCStorage.TooManyObjectToCompose(composeRequest.sourceObjects.size))
    else
      for {
        authResponse <- serverToServer.auth(settings.oauthSettings)
        request      <- composeObjectRequest(composeRequest, authResponse.access_token)
        response     <- client.expect[StorageObject](request)
      } yield response

  def copy(sourceBucket: String,
           sourceObject: String,
           destinationBucket: String,
           destinationObject: String,
           metadata: Option[StorageObject]): F[StorageObject] =
    for {
      authResponse <- serverToServer.auth(settings.oauthSettings)
      request <- copyObjectRequest(sourceBucket,
                                   sourceObject,
                                   destinationBucket,
                                   destinationObject,
                                   metadata,
                                   authResponse.access_token)
      response <- client.expect[StorageObject](request)
    } yield response

  // PRIVATE

  private def listBucketRequest(path: Path, token: String, nextPageToken: Option[String]): F[Request[F]] =
    for {
      uri <- Uri
              .fromString(s"https://${settings.host}/storage/${settings.version}/b/${settings.bucket}/o")
              .toTry
              .liftTo[F]
    } yield
      Request[F](
        method = Method.GET,
        uri = uri
          .withQueryParam("prefix", path)
          .withQueryParam("maxResults", "1")
          .withOptionQueryParam("pageToken", nextPageToken)
      ).withHeaders(headers.Authorization(Credentials.Token(AuthScheme.Bearer, token)))

  private def getObjectRequest(path: Path, token: String): F[Request[F]] =
    for {
      uri <- Uri
              .fromString(s"https://${settings.host}/storage/${settings.version}/b/${settings.bucket}/o/$path")
              .toTry
              .liftTo[F]
    } yield
      Request[F](
        method = Method.GET,
        uri = uri
      ).withHeaders(headers.Authorization(Credentials.Token(AuthScheme.Bearer, token)))

  private def downloadObjectRequest(path: Path, token: String): F[Request[F]] =
    for {
      uri <- Uri
              .fromString(s"https://${settings.host}/storage/${settings.version}/b/${settings.bucket}/o/$path")
              .toTry
              .liftTo[F]
    } yield
      Request[F](
        method = Method.GET,
        uri = uri.withQueryParam("alt", "media")
      ).withHeaders(headers.Authorization(Credentials.Token(AuthScheme.Bearer, token)))

  private def simpleUploadRequest(
    path: Path,
    token: String,
    body: Stream[F, Byte],
    contentType: headers.`Content-Type`
  ): F[Request[F]] =
    for {
      uri <- Uri
              .fromString(s"https://${settings.host}/upload/storage/${settings.version}/b/${settings.bucket}/o")
              .toTry
              .liftTo[F]
    } yield
      Request[F](
        method = Method.POST,
        uri = uri
          .withQueryParam("uploadType", "media")
          .withQueryParam("name", path),
        body = body
      ).withHeaders(
        headers.Authorization(Credentials.Token(AuthScheme.Bearer, token)),
        contentType
      )

  private def multipartUploadRequest(
    token: String,
    storageObject: StorageObject,
    body: Stream[F, Byte],
    contentType: headers.`Content-Type`
  ): F[Request[F]] = {
    val multipart = Multipart[F](
      Vector(
        Part.formData(
          "metadata",
          storageObject.asJson.toString,
          headers.`Content-Type`(MediaType.application.json, Charset.`UTF-8`)
        ),
        Part.fileData(
          "media",
          storageObject.name,
          body,
          contentType
        )
      )
    )

    val entity = EntityEncoder[F, Multipart[F]].toEntity(multipart)

    for {
      uri <- Uri
              .fromString(s"https://${settings.host}/upload/storage/${settings.version}/b/${settings.bucket}/o")
              .toTry
              .liftTo[F]
    } yield
      Request[F](
        method = Method.POST,
        uri = uri
          .withQueryParam("uploadType", "multipart"),
        body = entity.body
      ).withHeaders(
        headers.Authorization(Credentials.Token(AuthScheme.Bearer, token)),
        headers.`Content-Type`(MediaType.multipartType("related", Some(multipart.boundary.value)))
      )
  }

  private def composeObjectRequest(composeRequest: GCStorage.ComposeObjectRequest, token: String): F[Request[F]] =
    for {
      uri <- Uri
              .fromString(
                s"https://${settings.host}/storage/${settings.version}/b/${settings.bucket}/o/${composeRequest.destination.name}/compose"
              )
              .toTry
              .liftTo[F]
    } yield
      Request[F](
        method = Method.POST,
        uri = uri
      ).withHeaders(headers.Authorization(Credentials.Token(AuthScheme.Bearer, token)))
        .withEntity(composeRequest)

  private def copyObjectRequest(
    sourceBucket: String,
    sourceObject: String,
    destinationBucket: String,
    destinationObject: String,
    metadata: Option[StorageObject],
    token: String
  ): F[Request[F]] =
    for {
      uri <- Uri
              .fromString(
                s"https://${settings.host}/storage/${settings.version}/b/$sourceBucket/o/$sourceObject/copyTo/b/$destinationBucket/o/$destinationObject"
              )
              .toTry
              .liftTo[F]
    } yield {
      val request = Request[F](
        method = Method.POST,
        uri = uri
      ).withHeaders(headers.Authorization(Credentials.Token(AuthScheme.Bearer, token)))

      if (metadata.isDefined)
        request.withEntity(metadata.get)
      else request
    }

  private def removeRequest(path: Path, token: String): F[Request[F]] =
    for {
      uri <- Uri
              .fromString(s"https://${settings.host}/storage/${settings.version}/b/${settings.bucket}/o/$path")
              .toTry
              .liftTo[F]
    } yield
      Request[F](
        method = Method.DELETE,
        uri = uri
      ).withHeaders(headers.Authorization(Credentials.Token(AuthScheme.Bearer, token)))

  private def initiateResumableUploadRequest(
    authToken: String,
    storageObject: StorageObject
  ): F[Request[F]] =
    for {
      uri <- Uri
              .fromString(s"https://${settings.host}/upload/storage/${settings.version}/b/${settings.bucket}/o")
              .toTry
              .liftTo[F]
    } yield
      Request[F](
        method = Method.POST,
        uri = uri.withQueryParam("uploadType", "resumable")
      ).withHeaders(
          headers.Authorization(Credentials.Token(AuthScheme.Bearer, authToken))
        )
        .withEntity(
          storageObject
        )

  private def extractResumableUri(
    response: Response[F]
  ): F[Uri] =
    F.fromOption(
      response.headers.collectFirst { case headers.Location(location) => location.uri },
      new RuntimeException("Resumable uri is missing")
    )

  private def resumableChunkUploadRequest(
    resumableUri: Uri,
    chunkWithTotalSize: GCStorage.ChunkWithTotalSize
  ): Request[F] =
    Request[F](
      method = Method.PUT,
      uri = resumableUri,
      body = Stream.chunk(chunkWithTotalSize.chunk)
    ).withHeaders(
      headers.`Content-Length`.unsafeFromLong(chunkWithTotalSize.chunk.size.toLong),
      headers.`Content-Range`(
        headers.Range.SubRange(chunkWithTotalSize.rangeFrom, chunkWithTotalSize.rangeTo - 1),
        chunkWithTotalSize.totalSize
      ),
      headers.`Content-Type`(MediaType.application.`octet-stream`, Charset.`UTF-8`)
    )

  private def uploadResumableChunk(
    resumableUri: Uri,
    chunkWithTotalSize: GCStorage.ChunkWithTotalSize
  ): F[Option[StorageObject]] = {
    val request = resumableChunkUploadRequest(resumableUri, chunkWithTotalSize)
    client.fetch[Option[StorageObject]](request)(
      response =>
        if (response.status == Status.Ok)
          response.as[StorageObject].map(Some(_))
        else if (response.status == Status(308, "Resume Incomplete"))
          F.pure(Option.empty[StorageObject])
        else if (response.status.code / 100 == 5)
          F.raiseError[Option[StorageObject]](
            GCStorage.ResumableUploadServerError(
              status = response.status,
              body = "[body]",
              originalRequest = GCStorage.OriginalRequest(
                method = request.method,
                uri = request.uri,
                body = "[body]",
                headers = request.headers
              )
            )
          )
        else if (response.status == Status.TooManyRequests)
          F.raiseError[Option[StorageObject]](
            GCStorage.ResumableUploadTooManyRequests(
              status = response.status,
              body = "[body]",
              originalRequest = GCStorage.OriginalRequest(
                method = request.method,
                uri = request.uri,
                body = "[body]",
                headers = request.headers
              )
            )
          )
        else if (response.status == Status.NotFound)
          F.raiseError[Option[StorageObject]](
            GCStorage.ResumableUploadNotFound(
              status = response.status,
              body = "[body]",
              originalRequest = GCStorage.OriginalRequest(
                method = request.method,
                uri = request.uri,
                body = "[body]",
                headers = request.headers
              )
            )
          )
        else {
          F.raiseError[Option[StorageObject]](
            GCStorage.ResumableUploadRegularError(
              status = response.status,
              body = "[body]",
              originalRequest = GCStorage.OriginalRequest(
                method = request.method,
                uri = request.uri,
                body = "[body]",
                headers = request.headers
              )
            )
          )
      }
    )
  }

  private def reUploadResumableChunk(
    resumableUri: Uri,
    chunkWithTotalSize: GCStorage.ChunkWithTotalSize
  ): F[Option[StorageObject]] =
    for {
      // retry once
      uploadPosition <- getUploadLastPosition(resumableUri)
      reuploadChunkWithTotalSize = if (uploadPosition.isEmpty) chunkWithTotalSize
      else
        GCStorage.ChunkWithTotalSize(
          chunkWithTotalSize.chunk.drop((uploadPosition.get + 1 - chunkWithTotalSize.rangeFrom).toInt),
          uploadPosition.get + 1L,
          chunkWithTotalSize.rangeFrom,
          chunkWithTotalSize.totalSize.map(_ => chunkWithTotalSize.rangeFrom - uploadPosition.get)
        )
      result <- uploadResumableChunk(resumableUri, reuploadChunkWithTotalSize)
    } yield result

  private def getUploadLastPositionRequest(resumableUri: Uri): Request[F] =
    Request[F](
      method = Method.PUT,
      uri = resumableUri
    ).withHeaders(
      headers.`Content-Length`.unsafeFromLong(0)
    )

  private def getUploadLastPosition(resumableUri: Uri): F[Option[Long]] =
    client.fetch[Option[Long]](getUploadLastPositionRequest(resumableUri)) { response =>
      F.pure(response.headers.collectFirst { case headers.`Content-Range`(range) => range.length }.flatten)
    }

  private def chunkWithTotalSizePipe: Pipe[F, Chunk[Byte], GCStorage.ChunkWithTotalSize] = {

    /**
     * Delays output of chunk for one place so that when pull is complete chunk with stream total size is
     */
    def go(
      stream: Stream[F, Chunk[Byte]],
      chunkToPush: Chunk[Byte],
      size: Long
    ): fs2.Pull[F, GCStorage.ChunkWithTotalSize, Unit] =
      stream.pull.uncons1.flatMap {
        case Some((head, tail)) =>
          if (chunkToPush.isEmpty)
            go(tail, head, size)
          else
            fs2.Pull.output1(GCStorage.ChunkWithTotalSize(chunkToPush, size, size + chunkToPush.size, None)) >> go(
              tail,
              head,
              size + head.size
            )
        case None =>
          fs2.Pull.output1(
            GCStorage.ChunkWithTotalSize(chunkToPush, size, size + chunkToPush.size, Some(size + chunkToPush.size))
          ) >> fs2.Pull.done
      }

    in =>
      go(in, Chunk.empty[Byte], 0).stream
  }

}

object GCStorage {
  def apply[F[_]: ConcurrentEffect: Sleep](settings: Settings, client: Client[F]): GCStorage[F] =
    new GCStorage(settings, client)

  case class StorageObject(kind: String, id: String, name: String, generation: Option[Long] = None)

  case class ListBucketResponse(
    kind: String,
    nextPageToken: Option[String] = None,
    items: List[StorageObject]
  )

  case class ObjectPreconditions(ifGenerationMatch: Long)

  case class ComposeSourceObject(name: String, generation: Long, objectPreconditions: ObjectPreconditions)

  // use literal type
  case class ComposeObjectRequest(
    kind: String,
    sourceObjects: List[ComposeSourceObject],
    destination: StorageObject
  )

  case class RewriteResponse(
    kind: String,
    totalBytesRewritten: Long,
    objectSize: Long,
    done: Boolean,
    rewriteToken: String,
    resource: Option[StorageObject]
  )

  final case class OriginalRequest(method: Method, uri: Uri, body: String, headers: Headers)

  sealed trait GCStorageException extends Exception

  sealed trait GCStorageRestException extends GCStorageException {
    val status: Status
    val body: String
    val originalRequest: OriginalRequest
    override def getMessage(): String =
      s"""
         |response:
         | - status: $status
         | - body: $body
         |original request:
         | - method: ${originalRequest.method}
         | - uri: ${originalRequest.uri}
         | - body: ${originalRequest.body}
         | - headers: ${originalRequest.headers}
    """.stripMargin
  }
  final case class ResumableUploadServerError(status: Status, body: String, originalRequest: OriginalRequest)
      extends GCStorageRestException
  final case class ResumableUploadNotFound(status: Status, body: String, originalRequest: OriginalRequest)
      extends GCStorageRestException
  final case class ResumableUploadRegularError(status: Status, body: String, originalRequest: OriginalRequest)
      extends GCStorageRestException
  final case class ResumableUploadTooManyRequests(status: Status, body: String, originalRequest: OriginalRequest)
      extends GCStorageRestException

  final case class TooManyObjectToCompose(size: Int) extends GCStorageException {
    override def getMessage(): String = s"Given source objects size $size is greater than maximum one (32)."
  }

  case class ChunkWithTotalSize(chunk: Chunk[Byte], rangeFrom: Long, rangeTo: Long, totalSize: Option[Long])
}
