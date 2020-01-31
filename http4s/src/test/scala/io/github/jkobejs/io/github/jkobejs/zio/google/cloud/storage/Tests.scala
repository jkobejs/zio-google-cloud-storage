package io.github.jkobejs.io.github.jkobejs.zio.google.cloud.storage

import zio.test.DefaultRunnableSpec
import zio.test._
import io.github.jkobejs.zio.google.cloud.storage.integration.IntegrationTest

object Tests
    extends DefaultRunnableSpec(
      suite("All Google Cloud Storage Http4s tests")(
        IntegrationTest.http4SIntgrationSuite
      )
    )
