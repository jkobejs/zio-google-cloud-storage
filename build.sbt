val mainScala = "2.12.10"
val allScala  = Seq("2.13.1", mainScala)

inThisBuild(
  List(
    organization := "io.github.jkobejs",
    homepage := Some(url("https://github.com/jkobejs/zio-goolge-cloud-storage")),
    licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    organizationName := "Josip Grgurica",
    startYear := Some(2019),
    developers := List(
      Developer(
        "jkobejs",
        "Josip Grgurica",
        "josip.grgurica@gmail.com",
        url("https://github.com/jkobejs")
      )
    ),
    scalaVersion := mainScala,
    crossScalaVersions := allScala
  )
)

name := "zio-google-cloud-storage"

scalacOptions ++= Seq(
  "-deprecation",
  "-encoding",
  "UTF-8",
  "-explaintypes",
  "-Yrangepos",
  "-feature",
  "-language:higherKinds",
  "-language:existentials",
  "-unchecked",
  "-Xlint:_,-type-parameter-shadow",
  "-Ywarn-numeric-widen",
  "-Ywarn-unused:patvars,-implicits",
  "-Ywarn-value-discard"
) ++ (CrossVersion.partialVersion(scalaVersion.value) match {
  case Some((2, 12)) =>
    Seq(
      "-Xsource:2.13",
      "-Yno-adapted-args",
      "-Ypartial-unification",
      "-Ywarn-extra-implicit",
      "-Ywarn-inaccessible",
      "-Ywarn-infer-any",
      "-Ywarn-nullary-override",
      "-Ywarn-nullary-unit",
      "-opt-inline-from:<source>",
      "-opt-warnings",
      "-opt:l:inline"
    )
  case Some((2, 13)) =>
    Seq("-Ymacro-annotations")
  case _ => Nil
})

lazy val library =
  new {
    object Version {
      val scalatest            = "3.0.7"
      val wiremock             = "2.18.0"
      val catsRetry            = "0.3.0"
      val http4s               = "0.21.0-M6"
      val circe                = "0.12.3"
      val zio                  = "1.0.0-RC17"
      val zioInteropCats       = "2.0.0.0-RC10"
      val zioMacros            = "0.6.0"
      val zioGoogleCloudOauth2 = "0.0.1-M2+2-3b55023e-SNAPSHOT"
      val betterMonadicFor     = "0.3.1"
      val googleOauth4s        = "0.0.1+0-a8d86a4f+20191022-1416-SNAPSHOT"
      val sttpVersion          = "2.0.0-RC6"

    }

    val http4sBlazeClient    = "org.http4s"                   %% "http4s-blaze-client"            % Version.http4s
    val http4sCirce          = "org.http4s"                   %% "http4s-circe"                   % Version.http4s
    val circeCore            = "io.circe"                     %% "circe-core"                     % Version.circe
    val circeGeneric         = "io.circe"                     %% "circe-generic"                  % Version.circe
    val googleOauth4s        = "io.github.jkobejs"            %% "google-oauth4s"                 % Version.googleOauth4s
    val scalaTest            = "org.scalatest"                %% "scalatest"                      % Version.scalatest
    val wiremock             = "com.github.tomakehurst"       % "wiremock"                        % Version.wiremock
    val catsRetryCore        = "com.github.cb372"             %% "cats-retry-core"                % Version.catsRetry
    val catsRetryEffect      = "com.github.cb372"             %% "cats-retry-cats-effect"         % Version.catsRetry
    val zio                  = "dev.zio"                      %% "zio"                            % Version.zio
    val zioInteropCats       = "dev.zio"                      %% "zio-interop-cats"               % Version.zioInteropCats
    val zioTest              = "dev.zio"                      %% "zio-test"                       % Version.zio
    val zioTestSbt           = "dev.zio"                      %% "zio-test-sbt"                   % Version.zio
    val zioMacros            = "dev.zio"                      %% "zio-macros-core"                % Version.zioMacros
    val zioMacrosTest        = "dev.zio"                      %% "zio-macros-test"                % Version.zioMacros
    val zioGoogleCloudOauth2 = "io.github.jkobejs"            %% "zio-google-cloud-oauth2-http4s" % Version.zioGoogleCloudOauth2
    val betterMonadicFor     = "com.olegpy"                   %% "better-monadic-for"             % Version.betterMonadicFor
    val sttpZioStreamsClient = "com.softwaremill.sttp.client" %% "async-http-client-backend-fs2"  % Version.sttpVersion
    val sttpCore             = "com.softwaremill.sttp.client" %% "core"                           % Version.sttpVersion
    val sttpCirce            = "com.softwaremill.sttp.client" %% "circe"                          % Version.sttpVersion
    val logbackClassic       = "ch.qos.logback"               % "logback-classic"                 % "1.3.0-alpha5"
    val scalaLogging         = "com.typesafe.scala-logging"   %% "scala-logging"                  % "3.9.2"

  }

libraryDependencies ++= Seq(
  library.http4sBlazeClient,
  library.http4sCirce,
  library.googleOauth4s,
  library.catsRetryCore,
  library.catsRetryEffect,
  library.zio,
  library.zioInteropCats,
  library.zioMacros,
  library.zioMacrosTest,
  library.zioGoogleCloudOauth2,
  library.sttpCore,
  library.sttpZioStreamsClient,
  library.sttpCirce,
  library.logbackClassic,
  library.scalaLogging,
  library.zioTest    % Test,
  library.zioTestSbt % Test,
  library.scalaTest  % Test,
  library.wiremock   % Test,
  compilerPlugin(library.betterMonadicFor)
) ++ (CrossVersion.partialVersion(scalaVersion.value) match {
  case Some((2, x)) if x <= 12 =>
    Seq(
      compilerPlugin(("org.scalamacros" % "paradise" % "2.1.1").cross(CrossVersion.full))
    )
  case _ => Nil
})

addCompilerPlugin("org.typelevel" % "kind-projector" % "0.11.0" cross CrossVersion.full)

enablePlugins(MicrositesPlugin, BuildInfoPlugin)
micrositeTwitterCreator := "@jkobejs"
micrositeAuthor := "Josip Grgurica"
micrositeCompilingDocsTool := WithMdoc
micrositeGithubOwner := "jkobejs"
micrositeGithubRepo := "zio-google-cloud-storage"
micrositeBaseUrl := "zio-google-cloud-storage"
micrositeGithubToken := sys.env.get("GITHUB_TOKEN")
includeFilter in Jekyll := "*.html" | "*.css" | "*.png" | "*.jpg" | "*.gif" | "*.js" | "*.swf" | "*.md"

buildInfoKeys := Seq[BuildInfoKey](
  name,
  version,
  "serviceAccountKeyPath" -> sys.env.getOrElse("SERVICE_ACCOUNT_KEY_PATH", "")
)
buildInfoPackage := "io.github.jkobejs.zio.google.cloud.storage"

addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("check", "all scalafmtSbtCheck scalafmtCheck test:scalafmtCheck")
