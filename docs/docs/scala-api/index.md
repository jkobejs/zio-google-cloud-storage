---
layout: docs
title: Scala API
---

Scala API
----------------
Scala API module of `zio-google-cloud-storage` library implements Google Cloud Storage JSON API in terms of 
[ZIO][zio] and [ZStream][zstream]`, a powerful Scala effect type and its stream implementation.

### Objects


| Scala API | Implemented | Description | Google Storage link|
| ------------- | ------------- | ------------- | ------------- |
| compose| :heavy_check_mark: | Composes multiple storage objects | [https://cloud.google.com/storage/docs/json_api/v1/objects/compose](https://cloud.google.com/storage/docs/json_api/v1/objects/compose) |
| copy | - | Copies storage object from one location to another | [https://cloud.google.com/storage/docs/json_api/v1/objects/copy](https://cloud.google.com/storage/docs/json_api/v1/objects/copy) |
| delete | :heavy_check_mark: | Deletes storage object | [https://cloud.google.com/storage/docs/json_api/v1/objects/delete](https://cloud.google.com/storage/docs/json_api/v1/objects/delete) |
| get | :heavy_check_mark: | Gets storage object metadata | [https://cloud.google.com/storage/docs/json_api/v1/objects/get](https://cloud.google.com/storage/docs/json_api/v1/objects/get) |
| download | :heavy_check_mark: | Downloads storage object | [https://cloud.google.com/storage/docs/json_api/v1/objects/get](https://cloud.google.com/storage/docs/json_api/v1/objects/get)|
| simple upload | :heavy_check_mark: | Simple upload of storage object | [https://cloud.google.com/storage/docs/uploading-objects](https://cloud.google.com/storage/docs/uploading-objects) |
| multipart upload | :heavy_check_mark: | Multipart upload of storage object | [https://cloud.google.com/storage/docs/json_api/v1/how-tos/multipart-upload](https://cloud.google.com/storage/docs/json_api/v1/how-tos/multipart-upload) |
| resumable upload | :heavy_check_mark: | Resumable uploa dof storage object | [https://cloud.google.com/storage/docs/resumable-uploads](https://cloud.google.com/storage/docs/resumable-uploads) |
| list | :heavy_check_mark: | Lists content of bucket | [https://cloud.google.com/storage/docs/json_api/v1/objects/list](https://cloud.google.com/storage/docs/json_api/v1/objects/list])  |
| patch | - | Patches storage object | [https://cloud.google.com/storage/docs/json_api/v1/objects/patch](https://cloud.google.com/storage/docs/json_api/v1/objects/patch)  |
| rewrite | - | Rewrites storage objects from one location to another| [https://cloud.google.com/storage/docs/json_api/v1/objects/rewrite](https://cloud.google.com/storage/docs/json_api/v1/objects/rewrite) |
| update | - | Updates storage object data | [https://cloud.google.com/storage/docs/json_api/v1/objects/update](https://cloud.google.com/storage/docs/json_api/v1/objects/update) |
| move | - | Moves storage objects from one location to another| [https://cloud.google.com/storage/docs/json_api/v1/objects/rewrite](https://cloud.google.com/storage/docs/json_api/v1/objects/rewrite) |

[zio]: https://zio.dev/
[zstream]: https://zio.dev/docs/datatypes/datatypes_stream
