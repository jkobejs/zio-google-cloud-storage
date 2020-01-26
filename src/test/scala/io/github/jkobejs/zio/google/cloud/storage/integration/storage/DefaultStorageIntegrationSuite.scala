package io.github.jkobejs.zio.google.cloud.storage.integration.storage

import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

import io.github.jkobejs.zio.google.cloud.oauth2.server2server.authenticator.{ AuthApiClaims, AuthApiConfig }
import io.github.jkobejs.zio.google.cloud.oauth2.http4s.server2server.authenticator.Live
import io.github.jkobejs.zio.google.cloud.oauth2.server2server.serviceaccountkey.{
  FS2ServiceAccountKeyReader,
  ServiceAccountKey,
  ServiceAccountKeyReader
}
import io.github.jkobejs.zio.google.cloud.storage._
import io.github.jkobejs.zio.google.cloud.storage.http.SttpClient
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import sttp.client.SttpBackend
import sttp.client.asynchttpclient.ziostreams.AsyncHttpClientZioStreamsBackend
import zio.blocking.Blocking
import zio.duration.Duration
import zio.interop.catz._
import zio.stream.{ ZStream, ZStreamChunk }
import zio.test.Assertion.equalTo
import zio.test.{ assert, suite, testM, TestAspect }
import zio.{ Chunk, Task, ZIO }

object DefaultStorageIntegrationSuite {
  private val bucket = "alpakka"

  private val managedResource = ZIO
    .runtime[Any]
    .toManaged_
    .flatMap { implicit rts =>
      val exec = rts.platform.executor.asEC
      AsyncHttpClientZioStreamsBackend[Any](rts)
        .toManaged(_.close().mapError { error =>
          error.asInstanceOf[Nothing]
        })
        .flatMap(
          sttp => BlazeClientBuilder[Task](exec).resource.toManaged.map(http4s => (sttp, http4s))
        )
    }
    .map {
      case (sttp, client4s) =>
        new DefaultStorage with SttpClient with Live {
          override implicit val sttpBackend: SttpBackend[Task, ZStream[Any, Throwable, ByteBuffer], Nothing] =
            new LoggingSttpBackend(sttp)
          override val client: Client[Task] = client4s
        }
    }

  private def createConfig(serviceAccountKey: ServiceAccountKey) =
    CloudApiConfig(
      storageApiConfig = StorageApiConfig(
        "storage.googleapis.com",
        version = "v1"
      ),
      authApiConfig = AuthApiConfig(
        uri = serviceAccountKey.token_uri,
        privateKey = serviceAccountKey.private_key,
        grantType = "urn:ietf:params:oauth:grant-type:jwt-bearer"
      ),
      authApiClaims = AuthApiClaims(
        issuer = serviceAccountKey.client_email,
        scope = "https://www.googleapis.com/auth/devstorage.read_write",
        audience = serviceAccountKey.token_uri
      )
    )

  private val serviceAccountKeyReader =
    ServiceAccountKeyReader.>.readKey(io.github.jkobejs.zio.google.cloud.oauth2.BuildInfo.serviceAccountKeyPath)

  val defaultAuthenticatorIntegrationSuite =
    suite("Default Storage Integration tests")(
      testM("List bucket content") {
        val file1Content = Chunk.fromArray("File1 Content".getBytes)
        val file2Content = Chunk.fromArray("File2 Content".getBytes)
        val file1Name    = "listBucketFile1.txt"
        val file2Name    = "listBucketFile2.txt"
        for {
          serviceAccountKey <- serviceAccountKeyReader.provide(new FS2ServiceAccountKeyReader with Blocking.Live {})
          cloudApiConfig    = createConfig(serviceAccountKey)
          (file1, file2, files) <- managedResource.use {
                                    storage =>
                                      for {
                                        object1 <- storage.storage.simpleUpload(
                                                    cloudApiConfig,
                                                    bucket,
                                                    ZStreamChunk.fromChunks(file1Content).chunks,
                                                    file1Name
                                                  )
                                        file2 <- storage.storage.simpleUpload(
                                                  cloudApiConfig,
                                                  bucket,
                                                  ZStreamChunk.fromChunks(file2Content).chunks,
                                                  file2Name
                                                )
                                        files <- storage.storage
                                                  .list(cloudApiConfig, bucket, Some("listBucket"))
                                                  .runCollect
                                        _ <- storage.storage.delete(cloudApiConfig, bucket, file1Name)
                                        _ <- storage.storage.delete(cloudApiConfig, bucket, file2Name)
                                      } yield (object1, file2, files)
                                  }
        } yield assert(files.toSet, equalTo(Set(file1, file2)))
      } @@ TestAspect.ignore,
      testM("Get storage object") {
        val file1Content = Chunk.fromArray("File1 Content".getBytes)
        val file1Name    = "getStorageFile1.txt"
        for {
          serviceAccountKey <- serviceAccountKeyReader.provide(new FS2ServiceAccountKeyReader with Blocking.Live {})
          cloudApiConfig    = createConfig(serviceAccountKey)
          (uploaded, getObjectSome, getObjectNone) <- managedResource.use {
                                                       storage =>
                                                         for {
                                                           uploadedObject <- storage.storage.simpleUpload(
                                                                              cloudApiConfig,
                                                                              bucket,
                                                                              ZStreamChunk
                                                                                .fromChunks(file1Content)
                                                                                .chunks,
                                                                              file1Name
                                                                            )
                                                           getObjectSome <- storage.storage.get(
                                                                             cloudApiConfig,
                                                                             bucket,
                                                                             file1Name
                                                                           )
                                                           getObjectNone <- storage.storage
                                                                             .get(cloudApiConfig, bucket, "NONE")
                                                           _ <- storage.storage
                                                                 .delete(cloudApiConfig, bucket, file1Name)
                                                         } yield (uploadedObject, getObjectSome, getObjectNone)
                                                     }
        } yield {
          assert(Some(uploaded), equalTo(getObjectSome))
          assert(None, equalTo(getObjectNone))
        }
      } @@ TestAspect.ignore,
      testM("Compose storage objects") {
        val file1Content    = Chunk.fromArray("File1 Content".getBytes)
        val file2Content    = Chunk.fromArray("File2 Content".getBytes)
        val file1Name       = "composeFile1.txt"
        val file2Name       = "composeFile2.txt"
        val composeFileName = "composeFile.txt"
        for {
          serviceAccountKey <- serviceAccountKeyReader.provide(new FS2ServiceAccountKeyReader with Blocking.Live {})
          cloudApiConfig    = createConfig(serviceAccountKey)
          downloadContent <- managedResource.use {
                              storage =>
                                for {
                                  _ <- storage.storage.simpleUpload(
                                        cloudApiConfig,
                                        bucket,
                                        ZStreamChunk.fromChunks(file1Content).chunks,
                                        file1Name
                                      )
                                  _ <- storage.storage.simpleUpload(
                                        cloudApiConfig,
                                        bucket,
                                        ZStreamChunk.fromChunks(file2Content).chunks,
                                        file2Name
                                      )
                                  _ <- storage.storage.compose(
                                        cloudApiConfig,
                                        bucket,
                                        ComposeObject(
                                          sourceObjects = List(
                                            ComposeSourceObject(
                                              name = file1Name
                                            ),
                                            ComposeSourceObject(
                                              name = file2Name
                                            )
                                          ),
                                          destination = StorageObject(name = composeFileName)
                                        )
                                      )
                                  downloadContent <- storage.storage
                                                      .download(cloudApiConfig, bucket, composeFileName)
                                                      .runCollect
                                  _ <- storage.storage.delete(cloudApiConfig, bucket, file1Name)
                                  _ <- storage.storage.delete(cloudApiConfig, bucket, file2Name)
                                  _ <- storage.storage.delete(cloudApiConfig, bucket, composeFileName)
                                } yield downloadContent
                            }
        } yield assert(downloadContent, equalTo((file1Content ++ file2Content).toList))
      } @@ TestAspect.ignore,
      testM("Delete storage object") {
        val file1Content = Chunk.fromArray("File1 Content".getBytes)
        val file1Name    = "deleteObject.txt"

        for {
          serviceAccountKey <- serviceAccountKeyReader.provide(new FS2ServiceAccountKeyReader with Blocking.Live {})
          cloudApiConfig    = createConfig(serviceAccountKey)
          (firstResult, getObject, secondResult) <- managedResource.use {
                                                     storage =>
                                                       for {
                                                         _ <- storage.storage.simpleUpload(
                                                               cloudApiConfig,
                                                               bucket,
                                                               ZStreamChunk
                                                                 .fromChunks(file1Content)
                                                                 .chunks,
                                                               file1Name
                                                             )
                                                         firstDeletionResult <- storage.storage.delete(
                                                                                 cloudApiConfig,
                                                                                 bucket,
                                                                                 file1Name
                                                                               )
                                                         getObject <- storage.storage
                                                                       .get(
                                                                         cloudApiConfig,
                                                                         bucket,
                                                                         file1Name
                                                                       )
                                                         secondDeletionResult <- storage.storage.delete(
                                                                                  cloudApiConfig,
                                                                                  bucket,
                                                                                  file1Name
                                                                                )
                                                       } yield (
                                                         firstDeletionResult,
                                                         getObject,
                                                         secondDeletionResult
                                                       )
                                                   }
        } yield {
          assert(firstResult, equalTo(()))
          assert(getObject, equalTo(None))
          assert(secondResult, equalTo(()))
        }
      } @@ TestAspect.ignore,
      testM("Simple upload object") {
        val file1Content = Chunk.fromArray("File1 Content".getBytes)
        val file1Name    = "simpleUploadObject.txt"

        for {
          serviceAccountKey <- serviceAccountKeyReader.provide(new FS2ServiceAccountKeyReader with Blocking.Live {})
          cloudApiConfig    = createConfig(serviceAccountKey)
          (uploadResult, downloadContent) <- managedResource.use { storage =>
                                              for {
                                                uploadResult <- storage.storage.simpleUpload(
                                                                 cloudApiConfig,
                                                                 bucket,
                                                                 ZStreamChunk.fromChunks(file1Content).chunks,
                                                                 file1Name
                                                               )
                                                downloadContent <- storage.storage
                                                                    .download(cloudApiConfig, bucket, file1Name)
                                                                    .runCollect
                                                _ <- storage.storage.delete(cloudApiConfig, bucket, file1Name)
                                              } yield (uploadResult, downloadContent)
                                            }
        } yield {
          assert(uploadResult.name, equalTo(file1Name))
          assert(downloadContent, equalTo(file1Content.toList))
        }
      } @@ TestAspect.ignore,
      testM("Multipart upload object") {
        val file1Content = Chunk.fromArray("File1 Content".getBytes)
        val file1Name    = "multipartUploadObject.txt"

        for {
          serviceAccountKey <- serviceAccountKeyReader.provide(new FS2ServiceAccountKeyReader with Blocking.Live {})
          cloudApiConfig    = createConfig(serviceAccountKey)
          (uploadResult, downloadContent) <- managedResource.use {
                                              storage =>
                                                for {
                                                  uploadResult <- storage.storage
                                                                   .multipartUpload(
                                                                     cloudApiConfig,
                                                                     bucket,
                                                                     StorageObject(name = file1Name),
                                                                     ZStreamChunk.fromChunks(file1Content).chunks
                                                                   )
                                                                   .mapError { error =>
                                                                     println(error)
                                                                     error
                                                                   }
                                                  downloadContent <- storage.storage
                                                                      .download(cloudApiConfig, bucket, file1Name)
                                                                      .runCollect
                                                  _ <- storage.storage.delete(cloudApiConfig, bucket, file1Name)
                                                } yield (uploadResult, downloadContent)
                                            }
        } yield {
          assert(uploadResult.name, equalTo(file1Name))
          assert(downloadContent, equalTo(file1Content.toList))
        }
       } @@ TestAspect.ignore,
      testM("Resumable upload object") {
        val chunkSize = 256 * 1024
        val stream = zio.stream.Stream.fromChunk(
          Chunk.fromArray(List.fill(chunkSize)(192.toByte).toArray) ++
            Chunk.fromArray(List.fill(chunkSize)(190.toByte).toArray)
        )
        val file1Name = "resumableUploadObject.txt"

        for {
          serviceAccountKey <- serviceAccountKeyReader.provide(new FS2ServiceAccountKeyReader with Blocking.Live {})
          cloudApiConfig    = createConfig(serviceAccountKey)
          (uploadResult) <- managedResource.use { storage =>
                             for {
                               uploadResult <- stream
                                                .run(
                                                  storage.storage
                                                    .resumableUpload(
                                                      cloudApiConfig,
                                                      bucket,
                                                      StorageObject(name = file1Name)
                                                    )
                                                )
                                                .mapError { error =>
                                                  println(error.getMessage()); error
                                                }
                               // downloadContent <- storage.storage
                               // .download(cloudApiConfig, bucket, file1Name)
                               // .runCollect
                               // _ <- storage.storage.delete(cloudApiConfig, bucket, file1Name)
                             } yield (uploadResult)
                           }
        } yield {
          assert(uploadResult.name, equalTo(file1Name))
          // assert(downloadContent, equalTo(file1Content.toList))
        }
      }
    ) @@ TestAspect.ifEnvSet("SERVICE_ACCOUNT_KEY_PATH")
}
