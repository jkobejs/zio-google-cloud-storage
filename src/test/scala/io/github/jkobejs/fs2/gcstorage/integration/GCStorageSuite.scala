//package io.github.jkobejs.fs2.storage.integration
//
//import fs2.{text, Stream}
//import io.github.jkobejs.fs2.storage.GCStorage.{
//  ComposeObjectRequest,
//  ComposeSourceObject,
//  ObjectPreconditions,
//  StorageObject
//}
//import cats.implicits._
//
//class GCStorageSuite extends BaseIntegrationSuite {
//  testWithStorage(".put object") { storage =>
//    val body  = Stream.emits(List("This", " ", "my", " ", "first", " ", "file!\n")).through(text.utf8Encode)
//    val body2 = Stream.emits(List("This", " ", "my", " ", "secod", " ", "file!\n")).through(text.utf8Encode)
//    val path  = "1.txt"
//
//    val upload1 = body.through(storage.put(path))
//    val upload2 = body2.through(storage.put("2.txt"))
//
//    val upload = upload1.flatMap(_ => upload2)
//
//    upload.compile.toList.map { _ =>
//      assert(true)
//    }
//  }
//
//  testWithStorage(".multipartUpload object") { storage =>
//    val body          = Stream.emits(List("This", " ", "my", " ", "first multipart", " ", "file!")).through(text.utf8Encode)
//    val storageObject = StorageObject("storage#object", "my-id", "3.txt")
//
//    val upload = body.through(storage.multipartUpload(storageObject))
//
//    upload.compile.toList.map { uploaded =>
//      println(uploaded)
//      assert(true)
//    }
//  }
//
//  testWithStorage(".list bucket") { storage =>
//    storage
//      .list("")
//      .compile
//      .toList
//      .map { response =>
//        println(response)
//
//        assert(true)
//      }
//  }
//
//  testWithStorage(".get object") { storage =>
//    storage
//      .get("1.txt")
//      .map { result =>
//        println(result)
//
//        assert(true)
//      }
//  }
//
//  testWithStorage(".download object") { storage =>
//    storage.download("1.txt").through(text.utf8Decode).compile.toList.map { response =>
//      println(response)
//
//      assert(true)
//    }
//  }
//
//  testWithStorage(".resumableUpload") { storage =>
//    {
//      val body          = Stream.emits(List.fill(256 * 1024 * 2)("1")).through(text.utf8Encode)
//      val storageObject = StorageObject("storage#object", "my-id", "4.txt")
//
//      val resumableUpload = body.through(storage.resumableUpload(storageObject, 1))
//
//      resumableUpload.compile.toList.map { uploaded =>
//        println(uploaded)
//        assert(true)
//      }
//    }
//  }
//
//  testWithStorage(".compose") { storage =>
//    val composeAction = for {
//      storageObject1 <- storage.get("1.txt")
//      storageObject2 <- storage.get("2.txt")
//      _              = println(storageObject1)
//      _              = println(storageObject2)
//      composeSourceObject1 = ComposeSourceObject(
//        name = storageObject1.name,
//        generation = storageObject1.generation.getOrElse(0),
//        objectPreconditions = ObjectPreconditions(ifGenerationMatch = storageObject1.generation.getOrElse(0))
//      )
//      composeSourceObject2 = ComposeSourceObject(
//        name = storageObject2.name,
//        generation = storageObject2.generation.getOrElse(0),
//        objectPreconditions = ObjectPreconditions(ifGenerationMatch = storageObject2.generation.getOrElse(0))
//      )
//      composed <- storage.compose(
//                   ComposeObjectRequest("storage#object",
//                                        List(composeSourceObject1, composeSourceObject2),
//                                        StorageObject("storage#object", "", "composed.txt", None))
//                 )
//      _      = println(composed)
//      result <- storage.download("composed.txt").through(text.utf8Decode).compile.toList
//    } yield result
//
//    composeAction.map { composedText =>
//      println(composedText.mkString("\n"))
//
//      assert(true)
//    }
//  }
//
//  testWithStorage(".copy") { storage =>
//    val copyAction = for {
//      storageObject <- storage.copy(settings.bucket, "1.txt", settings.bucket, "copied.txt", None)
//      _             = println(storageObject)
//      result        <- storage.download("copied.txt").through(text.utf8Decode).compile.toList
//    } yield result.mkString("\n")
//
//    copyAction.map { result =>
//      println(result)
//      assert(true)
//    }
//  }
//
//  testWithStorage(".remove") { storage =>
//    List("1.txt", "2.txt", "3.txt", "4.txt", "composed.txt", "copied.txt").map(storage.remove).parSequence.map { result =>
//      assertResult(List.fill(6)(()))(result)
//    }
//  }
//
//}
