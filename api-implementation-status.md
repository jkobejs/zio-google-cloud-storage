# API implementation status

### Buckets

| Scala API | Implemented | Description | Google Storage link|
| ------------- | ------------- | ------------- | ------------- |
| delete | - | Deletes bucket | [https://cloud.google.com/storage/docs/json_api/v1/buckets/delete](https://cloud.google.com/storage/docs/json_api/v1/buckets/delete) |
| get | - | Gets bucket metadata | [https://cloud.google.com/storage/docs/json_api/v1/buckets/get](https://cloud.google.com/storage/docs/json_api/v1/buckets/get) |
| insert | - | Creates a new bucket | [https://cloud.google.com/storage/docs/json_api/v1/buckets/insert](https://cloud.google.com/storage/docs/json_api/v1/buckets/insert) |
| list | - | Retrieves a list of buckets for a given project | [https://cloud.google.com/storage/docs/json_api/v1/buckets/list](https://cloud.google.com/storage/docs/json_api/v1/buckets/list)  |
| patch | - | Patches bucket | [https://cloud.google.com/storage/docs/json_api/v1/buckets/patch](https://cloud.google.com/storage/docs/json_api/v1/buckets/patch)  |
| update | - | Updates bucket | [https://cloud.google.com/storage/docs/json_api/v1/buckets/update](https://cloud.google.com/storage/docs/json_api/v1/buckets/update)  |

### Objects

| Scala API | Implemented | Description | Google Storage link|
| ------------- | ------------- | ------------- | ------------- |
| compose| + | Composes multiple storage objects | [https://cloud.google.com/storage/docs/json_api/v1/objects/compose](https://cloud.google.com/storage/docs/json_api/v1/objects/compose) |
| copy | - | Copies storage object from one location to another | [https://cloud.google.com/storage/docs/json_api/v1/objects/copy](https://cloud.google.com/storage/docs/json_api/v1/objects/copy) |
| delete | + | Deletes storage object | [https://cloud.google.com/storage/docs/json_api/v1/objects/delete](https://cloud.google.com/storage/docs/json_api/v1/objects/delete) |
| get | + | Gets storage object metadata | [https://cloud.google.com/storage/docs/json_api/v1/objects/get](https://cloud.google.com/storage/docs/json_api/v1/objects/get) |
| download | + | Downloads storage object | [https://cloud.google.com/storage/docs/json_api/v1/objects/get](https://cloud.google.com/storage/docs/json_api/v1/objects/get)|
| simple upload | + | Simple upload of storage object | [https://cloud.google.com/storage/docs/uploading-objects](https://cloud.google.com/storage/docs/uploading-objects) |
| multipart upload | + | Multipart upload of storage object | [https://cloud.google.com/storage/docs/json_api/v1/how-tos/multipart-upload](https://cloud.google.com/storage/docs/json_api/v1/how-tos/multipart-upload) |
| resumable upload | + | Resumable uploa dof storage object | [https://cloud.google.com/storage/docs/resumable-uploads](https://cloud.google.com/storage/docs/resumable-uploads) |
| list | + | Lists content of bucket | [https://cloud.google.com/storage/docs/json_api/v1/objects/list](https://cloud.google.com/storage/docs/json_api/v1/objects/list)  |
| patch | - | Patches storage object | [https://cloud.google.com/storage/docs/json_api/v1/objects/patch](https://cloud.google.com/storage/docs/json_api/v1/objects/patch)  |
| rewrite | - | Rewrites storage objects from one location to another| [https://cloud.google.com/storage/docs/json_api/v1/objects/rewrite](https://cloud.google.com/storage/docs/json_api/v1/objects/rewrite) |
| update | - | Updates storage object data | [https://cloud.google.com/storage/docs/json_api/v1/objects/update](https://cloud.google.com/storage/docs/json_api/v1/objects/update) |
| move | - | Moves storage objects from one location to another| [https://cloud.google.com/storage/docs/json_api/v1/objects/rewrite](https://cloud.google.com/storage/docs/json_api/v1/objects/rewrite) |