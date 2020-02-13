package io.github.jkobejs.zio.google.cloud.storage.sttp.integration

import zio.ZIO
import zio.Task
import org.http4s.client.blaze.BlazeClientBuilder
import io.github.jkobejs.zio.google.cloud.storage.DefaultStorage
import io.github.jkobejs.zio.google.cloud.oauth2.http4s.server2server.authenticator.Live
import org.http4s.client.Client
import zio.interop.catz._
import sttp.client.asynchttpclient.fs2.AsyncHttpClientFs2Backend
import io.github.jkobejs.zio.google.cloud.storage.sttp.http.SttpClient
import sttp.client.SttpBackend
import java.nio.ByteBuffer
import io.github.jkobejs.zio.google.cloud.storage.integration.DefaultStorageIntegrationSuite

object IntegrationTest {
  private val sttpManagedResource = ZIO
    .runtime[Any]
    .toManaged_
    .flatMap { implicit rts =>
      val exec = rts.platform.executor.asEC
      AsyncHttpClientFs2Backend
        .resource[Task]()
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

  val sttpIntegrationSuite =
    DefaultStorageIntegrationSuite("Sttp integration", sttpManagedResource).defaultAuthenticatorIntegrationSuite
}
