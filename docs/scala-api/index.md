---
layout: docs
title: Scala API
---

Scala API
----------------
Scala API module of `zio-google-cloud-storage` library implements Google Cloud Storage JSON API in terms of 
[ZIO][zio] and [ZStream][zstream]`, a powerful Scala effect type and its stream implementation.


### Bucket Access Controls
Not implemented!

### Buckets
Not implemented!

### Channels
Not implemented!

### Default Object Access Controls
Not implemented!

### Notifications
Not implemented!

### Object Access Controls
Not implemented!

### Objects


| Scala API | Implemented | Description | Google Storage link|
| ------------- | ------------- | ------------- | ------------- |
| [compose](#compose)| - | Composes multiple storage objects | [https://cloud.google.com/storage/docs/json_api/v1/objects/compose](https://cloud.google.com/storage/docs/json_api/v1/objects/compose) |
| [copy](#copy)| - | Copies storage object from one location to another | [https://cloud.google.com/storage/docs/json_api/v1/objects/copy](https://cloud.google.com/storage/docs/json_api/v1/objects/copy) |
| [delete](#delete)| - | Deletes storage object | [https://cloud.google.com/storage/docs/json_api/v1/objects/delete](https://cloud.google.com/storage/docs/json_api/v1/objects/delete) |
| [get](#get)| + | Gets storage object metadata | [https://cloud.google.com/storage/docs/json_api/v1/objects/get](https://cloud.google.com/storage/docs/json_api/v1/objects/get) |
| [download](#download)| - | Downloads storage object | [https://cloud.google.com/storage/docs/json_api/v1/objects/get](https://cloud.google.com/storage/docs/json_api/v1/objects/get)|
| [simple upload](#simple-upload)| - | Simple upload of storage object | [https://cloud.google.com/storage/docs/uploading-objects](https://cloud.google.com/storage/docs/uploading-objects) |
| [multipart upload](#multipart-upload)| - | Multipart upload of storage object | [https://cloud.google.com/storage/docs/json_api/v1/how-tos/multipart-upload](https://cloud.google.com/storage/docs/json_api/v1/how-tos/multipart-upload) |
| [resumable upload](#resumable-upload)| - | Resumable uploa dof storage object | [https://cloud.google.com/storage/docs/resumable-uploads](https://cloud.google.com/storage/docs/resumable-uploads) |
| [list](#list)| + | Lists content of bucket | [https://cloud.google.com/storage/docs/json_api/v1/objects/list](https://cloud.google.com/storage/docs/json_api/v1/objects/list])  |
| [patch](#patch)| + | Patches storage object | [https://cloud.google.com/storage/docs/json_api/v1/objects/patch](https://cloud.google.com/storage/docs/json_api/v1/objects/patch)  |
| [rewrite](#rewrite)| - | Rewrites storage objects from one location to another| [https://cloud.google.com/storage/docs/json_api/v1/objects/rewrite](https://cloud.google.com/storage/docs/json_api/v1/objects/rewrite) |
| [update](#update)| - | Updates storage object data | [https://cloud.google.com/storage/docs/json_api/v1/objects/update](https://cloud.google.com/storage/docs/json_api/v1/objects/update) |
| [move](#move)| - | Moves storage objects from one location to another| [https://cloud.google.com/storage/docs/json_api/v1/objects/rewrite](https://cloud.google.com/storage/docs/json_api/v1/objects/rewrite) |


#### Compose
```scala
def compose(cloudApiConfig: CloudApiConfig,
                bucket: String,
                composeRequest: ComposeObject): ZIO[R, StorageError, StorageObject]
```

#### Copy
```scala
def copy(cloudApiConfig: CloudApiConfig,
             bucket: String,
             sourceBucket: String,
             sourceObject: String,
             destinationBucket: String,
             destinationObject: String,
             metadata: Option[StorageObject]): ZIO[R, StorageError, StorageObject]
```

#### Delete
```scala
def delete(cloudApiConfig: CloudApiConfig, bucket: String, path: Path): ZIO[R, StorageError, Unit]
```

#### Get
```scala
def get(cloudApiConfig: CloudApiConfig, bucket: String, path: Path): ZIO[R, StorageError, StorageObject]
```

#### Download
```scala
def download(cloudApiConfig: CloudApiConfig, bucket: String, path: Path): ZStream[R, StorageError, Byte]
```

#### Simple upload
```scala
def simpleUpload(cloudApiConfig: CloudApiConfig, bucket: String, path: Path): ZSink[R, StorageError, Byte, Byte, Unit]
```

#### Multipart upload
```scala
def multipartUpload(cloudApiConfig: CloudApiConfig,
                        bucket: String,
                        storageObject: StorageObject): ZSink[R, StorageError, Byte, Byte, StorageObject]
```

#### Resumable upload
```scala
def resumableUpload(cloudApiConfig: CloudApiConfig,
                        bucket: String,
                        storageObject: StorageObject,
                        chunkMultiple: Int): ZSink[R, StorageError, Byte, Byte, StorageObject]
```


#### List
```scala
def list(cloudApiConfig: CloudApiConfig, bucket: String, path: Path): ZStream[R, StorageError, StorageObject]
```

#### Patch
```scala
???
```


#### Rewrite
```scala
def rewrite(cloudApiConfig: CloudApiConfig,
             bucket: String,
             source: Path,
             destination: Path): ZIO[R, StorageError, Unit]
```

#### Update
```scala
???
```

#### Modify all
```scala
???
```

### Hmac Keys

### Service Account

[zio]: https://zio.dev/
[zstream]: https://zio.dev/docs/datatypes/datatypes_stream
