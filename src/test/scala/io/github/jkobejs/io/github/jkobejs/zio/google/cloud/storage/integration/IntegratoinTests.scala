package io.github.jkobejs.zio.google.cloud.storage.integration

import zio.ZIO
import zio.Task
import org.http4s.client.blaze.BlazeClientBuilder
import io.github.jkobejs.zio.google.cloud.storage.DefaultStorage
import io.github.jkobejs.zio.google.cloud.oauth2.http4s.server2server.authenticator.Live
import io.github.jkobejs.zio.google.cloud.storage.http.{ Http4sClient => StorageHttp4sClient }
import org.http4s.client.Client
import zio.interop.catz._
import sttp.client.asynchttpclient.fs2.AsyncHttpClientFs2Backend
import io.github.jkobejs.zio.google.cloud.storage.http.SttpClient
import sttp.client.SttpBackend
import java.nio.ByteBuffer

object IntegrationTests {
  val http4sManaged = ZIO
    .runtime[Any]
    .toManaged_
    .flatMap { implicit rts =>
      val exec = rts.platform.executor.asEC
      BlazeClientBuilder[Task](exec).resource.toManaged
    }
    .map {
      case (client4s) =>
        new DefaultStorage with StorageHttp4sClient with Live {
          override val client: Client[Task] = client4s
        }
    }
  private val sttpManagedResource = ZIO
    .runtime[Any]
    .toManaged_
    .flatMap { implicit rts =>
      val exec = rts.platform.executor.asEC
      AsyncHttpClientFs2Backend
        .resource()
        .toManaged
        .flatMap(
          sttp => BlazeClientBuilder[Task](exec).resource.toManaged.map(http4s => (sttp, http4s))
        )
    }
    .map {
      case (sttp, client4s) =>
        new DefaultStorage with SttpClient with Live {
          override implicit val sttpBackend: SttpBackend[Task, fs2.Stream[Task, ByteBuffer], Nothing] =
            new LoggingSttpBackend(sttp)
          override val client: Client[Task] = client4s
        }
    }

  val http4SIntgrationSuite =
    DefaultStorageIntegrationSuite("Http4s integration", http4sManaged).defaultAuthenticatorIntegrationSuite
  val sttpIntegrationSuite =
    DefaultStorageIntegrationSuite("Sttp integration", sttpManagedResource).defaultAuthenticatorIntegrationSuite
}
