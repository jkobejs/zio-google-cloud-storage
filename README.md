# Google Cloud Storage for Scala

**zio-google-cloud-storage** is effectful API for [Google Cloud Storage][google-storage] flows for Scala.

[![Build Status](https://travis-ci.com/jkobejs/zio-google-cloud-storage.svg?branch=master)](https://travis-ci.com/jkobejs/zio-google-cloud-storage)
[![Latest Version](https://maven-badges.herokuapp.com/maven-central/io.github.jkobejs/zio-google-cloud-storage_2.12/badge.svg)](https://search.maven.org/#search%7Cga%7C1%7Cg%3A"io.github.jkobejs"%20zio-google-cloud-storage)
[![License](http://img.shields.io/:license-Apache%202-green.svg)](http://www.apache.org/licenses/LICENSE-2.0.txt)

Please proceed to the [microsite][microsite] for more information.

[google-storage]: https://developers.google.com/identity/protocols/OAuth2
[microsite]: https://jkobejs.github.io/zio-google-cloud-storage/

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

### Roadmap

#### Version 0.0.1

- implement missing object and bucket APIs - [current status](api-implementation-status.md)
- implement bucket API
- add support for all fields in domain objects
- add support for standard query paramaters
- add support for missing query parameters (API method related)
- improve error messages
- implement retries
- support for encryption
- don't depend on auth library
- unit tests

#### Version 0.0.1

- zio streams base API
- graphQL API