package io.github.jkobejs.io.github.jkobejs.zio.google.cloud.storage

import io.github.jkobejs.zio.google.cloud.storage.integration.storage.DefaultStorageIntegrationSuite
import zio.test.DefaultRunnableSpec
import zio.test._

object Tests
    extends DefaultRunnableSpec(
      suite("All Google Cloud Storage tests")(DefaultStorageIntegrationSuite.defaultAuthenticatorIntegrationSuite)
    )
