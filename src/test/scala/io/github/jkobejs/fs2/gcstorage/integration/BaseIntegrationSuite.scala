//package io.github.jkobejs.fs2.storage.integration
//
//import org.scalatest.FunSuiteLike
//import scala.concurrent.ExecutionContext
//import org.scalatest.concurrent.ScalaFutures
//import io.github.jkobejs.google.oauth4s.ServiceAccountKeyReader
//import cats.effect.IO
//import io.github.jkobejs.google.oauth4s.ServerToServer
//import java.time.Instant
//import io.github.jkobejs.fs2.storage.Settings
//import org.scalatest.time.Millis
//import org.scalatest.time.Span
//import org.scalatest.time.Seconds
//import org.http4s.client.blaze.BlazeClientBuilder
//import io.github.jkobejs.fs2.storage.GCStorage
//import org.scalatest.compatible.Assertion
//import org.scalatest.Tag
//import retry.CatsEffect._
//import cats.effect.Timer
//
//trait BaseIntegrationSuite extends FunSuiteLike with ScalaFutures {
//  implicit val executionContext: ExecutionContext =
//    scala.concurrent.ExecutionContext.Implicits.global
//  implicit val ctx = IO.contextShift(executionContext)
//  implicit val timer: Timer[IO] = IO.timer(executionContext)
//
//
//  implicit val defaultPatience: PatienceConfig =
//    PatienceConfig(timeout = Span(5, Seconds), interval = Span(100, Millis))
//
//  val clientResource = BlazeClientBuilder[IO](executionContext).resource
//
//  val serviceAccountKey = ServiceAccountKeyReader
//    .readServiceAccountKey[IO](io.github.jkobejs.fs2.BuildInfo.serviceAccountKeyPath, executionContext)
//    .unsafeToFuture()
//    .futureValue
//
//  val settings = Settings(
//    bucket = "alpakka",
//    oauthSettings =   ServerToServer.Settings(
//      uri = serviceAccountKey.token_uri,
//      privateKey = serviceAccountKey.private_key,
//      grantType = "urn:ietf:params:oauth:grant-type:jwt-bearer",
//      claims = ServerToServer.GoogleClaims(
//        issuer = serviceAccountKey.client_email,
//        scope = "https://www.googleapis.com/auth/devstorage.read_write",
//        audience = serviceAccountKey.token_uri,
//        expiration = Instant.now().plusSeconds(3600),
//        issuedAt = Instant.now()
//      )
//    )
//  )
//
//
//  def testWithStorage(testName: String, testTags: Tag*)(fnc: GCStorage[IO] => IO[Assertion]): Unit =
//    test(testName, testTags: _*) {
//      clientResource
//        .use { client =>
//          fnc(new GCStorage[IO](settings, client))
//        }
//        .unsafeToFuture()
//        .futureValue
//    }
//
//}
