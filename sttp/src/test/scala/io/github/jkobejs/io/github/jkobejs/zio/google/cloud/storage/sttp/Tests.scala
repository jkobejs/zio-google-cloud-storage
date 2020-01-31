package io.github.jkobejs.io.github.jkobejs.zio.google.cloud.storage.sttp

import zio.test.DefaultRunnableSpec
import zio.test._
import io.github.jkobejs.zio.google.cloud.storage.sttp.integration.IntegrationTest

object Tests
    extends DefaultRunnableSpec(
      suite("All Google Cloud Sttp Storage tests")(
        IntegrationTest.sttpIntegrationSuite
      )
    )
