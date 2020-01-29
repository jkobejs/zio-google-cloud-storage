package io.github.jkobejs.zio.google.cloud.storage.integration

import io.github.jkobejs.zio.google.cloud.oauth2.server2server.authenticator.{ AuthApiClaims, AuthApiConfig }
import io.github.jkobejs.zio.google.cloud.oauth2.server2server.serviceaccountkey.{
  FS2ServiceAccountKeyReader,
  ServiceAccountKey,
  ServiceAccountKeyReader
}
import io.github.jkobejs.zio.google.cloud.storage._
import zio.interop.catz._
import zio.test.Assertion.equalTo
import zio.test.{ assert, suite, testM, TestAspect }
import fs2.{ text, Stream }
import zio.ZManaged

trait DefaultStorageIntegrationSuite {
  private val bucket = "alpakka"

  val managedResource: ZManaged[Any, Throwable, Storage]
  val suiteName: String

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

  def defaultAuthenticatorIntegrationSuite =
    suite(suiteName)(
      testM("List bucket content") {
        val file1Content = Stream.emit("File1 Content").through(text.utf8Encode)
        val file2Content = Stream.emit("File2 Content").through(text.utf8Encode)
        val file1Name    = s"$suiteName-listBucketFile1.txt"
        val file2Name    = s"$suiteName-listBucketFile2.txt"
        for {
          serviceAccountKey <- serviceAccountKeyReader.provide(FS2ServiceAccountKeyReader())
          cloudApiConfig    = createConfig(serviceAccountKey)
          (file1, file2, files) <- managedResource.use {
                                    storage =>
                                      for {
                                        object1 <- file1Content
                                                    .through(
                                                      storage.storage.simpleUpload(
                                                        cloudApiConfig,
                                                        bucket,
                                                        file1Name
                                                      )
                                                    )
                                                    .compile
                                                    .toList
                                        file2 <- file2Content
                                                  .through(
                                                    storage.storage.simpleUpload(
                                                      cloudApiConfig,
                                                      bucket,
                                                      file2Name
                                                    )
                                                  )
                                                  .compile
                                                  .toList
                                        files <- storage.storage
                                                  .list(cloudApiConfig, bucket, Some(s"$suiteName-listBucket"))
                                                  .compile
                                                  .toList
                                        _ <- storage.storage.delete(cloudApiConfig, bucket, file1Name)
                                        _ <- storage.storage.delete(cloudApiConfig, bucket, file2Name)
                                      } yield (object1.head, file2.head, files)
                                  }
        } yield assert(files.toSet, equalTo(Set(file1, file2)))
      },
      testM("Get storage object") {
        val file1Content = Stream.emit("File1 Content").through(text.utf8Encode)
        val file1Name    = s"$suiteName-getStorageFile1.txt"
        for {
          serviceAccountKey <- serviceAccountKeyReader.provide(FS2ServiceAccountKeyReader())
          cloudApiConfig    = createConfig(serviceAccountKey)
          (uploaded, getObjectSome, getObjectNone) <- managedResource.use {
                                                       storage =>
                                                         for {
                                                           uploadedObject <- file1Content
                                                                              .through(
                                                                                storage.storage.simpleUpload(
                                                                                  cloudApiConfig,
                                                                                  bucket,
                                                                                  file1Name
                                                                                )
                                                                              )
                                                                              .compile
                                                                              .toList
                                                           getObjectSome <- storage.storage.get(
                                                                             cloudApiConfig,
                                                                             bucket,
                                                                             file1Name
                                                                           )
                                                           getObjectNone <- storage.storage
                                                                             .get(cloudApiConfig, bucket, "NONE")
                                                           _ <- storage.storage
                                                                 .delete(cloudApiConfig, bucket, file1Name)
                                                         } yield (uploadedObject.head, getObjectSome, getObjectNone)
                                                     }
        } yield {
          assert(Some(uploaded), equalTo(getObjectSome))
          assert(None, equalTo(getObjectNone))
        }
      },
      testM("Compose storage objects") {
        val file1Content    = Stream.emit("File1 Content").through(text.utf8Encode)
        val file2Content    = Stream.emit("File2 Content").through(text.utf8Encode)
        val file1Name       = s"$suiteName-composeFile1.txt"
        val file2Name       = s"$suiteName-composeFile2.txt"
        val composeFileName = s"$suiteName-composeFile.txt"
        for {
          serviceAccountKey <- serviceAccountKeyReader.provide(FS2ServiceAccountKeyReader())
          cloudApiConfig    = createConfig(serviceAccountKey)
          downloadContent <- managedResource.use {
                              storage =>
                                for {
                                  _ <- file1Content
                                        .through(
                                          storage.storage.simpleUpload(
                                            cloudApiConfig,
                                            bucket,
                                            file1Name
                                          )
                                        )
                                        .compile
                                        .toList
                                  _ <- file2Content
                                        .through(
                                          storage.storage.simpleUpload(
                                            cloudApiConfig,
                                            bucket,
                                            file2Name
                                          )
                                        )
                                        .compile
                                        .toList
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
                                                      .compile
                                                      .toList
                                  _ <- storage.storage.delete(cloudApiConfig, bucket, file1Name)
                                  _ <- storage.storage.delete(cloudApiConfig, bucket, file2Name)
                                  _ <- storage.storage.delete(cloudApiConfig, bucket, composeFileName)
                                } yield downloadContent
                            }
        } yield assert(downloadContent, equalTo((file1Content ++ file2Content).toList))
      },
      testM("Delete storage object") {
        val file1Content = Stream.emit("File1 Content").through(text.utf8Encode)
        val file1Name    = s"$suiteName-deleteObject.txt"

        for {
          serviceAccountKey <- serviceAccountKeyReader.provide(FS2ServiceAccountKeyReader())
          cloudApiConfig    = createConfig(serviceAccountKey)
          (firstResult, getObject, secondResult) <- managedResource.use {
                                                     storage =>
                                                       for {
                                                         _ <- file1Content
                                                               .through(
                                                                 storage.storage.simpleUpload(
                                                                   cloudApiConfig,
                                                                   bucket,
                                                                   file1Name
                                                                 )
                                                               )
                                                               .compile
                                                               .toList
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
      },
      testM("Simple upload object") {
        val file1Content = Stream.emit("File1 Content").through(text.utf8Encode)
        val file1Name    = s"$suiteName-simpleUploadObject.txt"

        for {
          serviceAccountKey <- serviceAccountKeyReader.provide(FS2ServiceAccountKeyReader())
          cloudApiConfig    = createConfig(serviceAccountKey)
          (uploadResult, downloadContent) <- managedResource.use { storage =>
                                              for {
                                                uploadResult <- file1Content
                                                                 .through(
                                                                   storage.storage.simpleUpload(
                                                                     cloudApiConfig,
                                                                     bucket,
                                                                     file1Name
                                                                   )
                                                                 )
                                                                 .compile
                                                                 .toList
                                                downloadContent <- storage.storage
                                                                    .download(cloudApiConfig, bucket, file1Name)
                                                                    .compile
                                                                    .toList
                                                _ <- storage.storage.delete(cloudApiConfig, bucket, file1Name)
                                              } yield (uploadResult.head, downloadContent)
                                            }
        } yield {
          assert(uploadResult.name, equalTo(file1Name))
          assert(downloadContent, equalTo(file1Content.toList))
        }
      },
      testM("Multipart upload object") {
        val file1Content = Stream.emit("File1 Content").through(text.utf8Encode)
        val file1Name    = s"$suiteName-multipartUploadObject.txt"

        for {
          serviceAccountKey <- serviceAccountKeyReader.provide(FS2ServiceAccountKeyReader())
          cloudApiConfig    = createConfig(serviceAccountKey)
          (uploadResult, downloadContent) <- managedResource.use { storage =>
                                              for {
                                                uploadResult <- file1Content
                                                                 .through(
                                                                   storage.storage
                                                                     .multipartUpload(
                                                                       cloudApiConfig,
                                                                       bucket,
                                                                       StorageObject(name = file1Name)
                                                                     )
                                                                 )
                                                                 .compile
                                                                 .toList
                                                downloadContent <- storage.storage
                                                                    .download(cloudApiConfig, bucket, file1Name)
                                                                    .compile
                                                                    .toList
                                                _ <- storage.storage.delete(cloudApiConfig, bucket, file1Name)
                                              } yield (uploadResult.head, downloadContent)
                                            }
        } yield {
          assert(uploadResult.name, equalTo(file1Name))
          assert(downloadContent, equalTo(file1Content.toList))
        }
      },
      testM("Resumable upload object") {
        val chunkSize = 256 * 1024
        val stream    = Stream.emits(List.fill(chunkSize * 2)("1")).through(text.utf8Encode)
        val file1Name = s"$suiteName-resumableUploadObject.txt"

        for {
          serviceAccountKey <- serviceAccountKeyReader.provide(FS2ServiceAccountKeyReader())
          cloudApiConfig    = createConfig(serviceAccountKey)
          (uploadResult, downloadContent) <- managedResource.use { storage =>
                                              for {
                                                uploadResult <- stream
                                                                 .through(
                                                                   storage.storage
                                                                     .resumableUpload(
                                                                       cloudApiConfig,
                                                                       bucket,
                                                                       StorageObject(name = file1Name)
                                                                     )
                                                                 )
                                                                 .compile
                                                                 .toList
                                                downloadContent <- storage.storage
                                                                    .download(cloudApiConfig, bucket, file1Name)
                                                                    .compile
                                                                    .toList
                                                _ <- storage.storage.delete(cloudApiConfig, bucket, file1Name)
                                              } yield (uploadResult.head, downloadContent)
                                            }
        } yield {
          assert(uploadResult.name, equalTo(file1Name))
          assert(downloadContent, equalTo(stream.toList))
        }
      }
    ) @@ TestAspect.ifEnvSet("SERVICE_ACCOUNT_KEY_PATH")
}

object DefaultStorageIntegrationSuite {
  def apply(suiteN: String, zmanagedResource: ZManaged[Any, Throwable, Storage]): DefaultStorageIntegrationSuite =
    new DefaultStorageIntegrationSuite {
      override val suiteName: String                                  = suiteN
      override val managedResource: ZManaged[Any, Throwable, Storage] = zmanagedResource
    }
}
