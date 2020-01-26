---
layout: home
---

# Google Cloud Storage for Scala

Effectful API for [Google Cloud Storage][google-storage] API for Scala.

[![Build Status](https://travis-ci.com/jkobejs/zio-google-cloud-storage.svg?branch=master)](https://travis-ci.com/jkobejs/zio-google-cloud-storage)
[![Latest Version](https://maven-badges.herokuapp.com/maven-central/io.github.jkobejs/zio-google-cloud-storage_2.12/badge.svg)](https://search.maven.org/#search%7Cga%7C1%7Cg%3A"io.github.jkobejs"%20zio-google-cloud-storage)
[![License](http://img.shields.io/:license-Apache%202-green.svg)](http://www.apache.org/licenses/LICENSE-2.0.txt)

Cloud Storage allows world-wide storage and retrieval of any amount of data at any time.
You can use Cloud Storage for a range of scenarios including serving website content, 
storing data for archival and disaster recovery, or distributing large data objects to users via direct download.

Quick start
------------
The current version is **{{site.zioGoogleCloudStorageVersion}}** for **Scala 2.12/13** with
- [tsec][tsec] {{site.tsecVersion}}
- [http4s][http4s] {{site.http4sVersion}}
- [circe][circe] {{site.circeVersion}}
- [zio][zio] {{site.zioVersion}}
- [zio-macros][zio-macros] {{site.zioMacrosVersion}}
- [better-monadic-for][better-monadic-for] {{site.betterMonadicForVersion}}

To use library add this to `build.sbt` file:
```scala
libraryDependencies += "com.jkobejs" %% "zio-google-cloud-storage" % "{{site.zioGoogleCloudStorageVersion}}"
```

### Note on milestones:
Our Notation for versions is:
```
X.X.X
^ ^ ^____Minor
| |______Major
|________Complete redesign (i.e scalaz 7 vs 8)  
```

All `x.x.x-Mx` releases are milestone releases. Thus, we do not guarantee binary compatibility or no api-breakage until
a concrete version(i.e `0.0.1`). We aim to keep userland-apis relatively stable, but 
internals shift as we find better/more performant abstractions.

We will guarantee compatibility between minor versions (i.e 0.0.1 => 0.0.2) but not major versions (0.0.1 => 0.1.0)

Scala API
----------------
[Documentation][scala-api] of server to server auth.

GraphQL API
-----------------------
Not Implemented!


[google-storage]: https://developers.google.com/identity/protocols/OAuth2
[tsec]: https://jmcardon.github.io/tsec/
[http4s]: https://http4s.org/
[scala-api]: scala-api
[circe]: https://circe.github.io/circe/
[zio]: https://zio.dev
[zio-macros]: https://github.com/zio/zio-macros
[better-monadic-for]: https://github.com/oleg-py/better-monadic-for
