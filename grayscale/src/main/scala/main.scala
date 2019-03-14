import java.io.{File, IOException}
import java.nio.file.Paths
import java.util.UUID.randomUUID

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.FileInfo
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.FileIO
import org.apache.commons.io.{FilenameUtils, IOUtils}
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.ContentType
import org.apache.http.entity.mime.content.{FileBody, StringBody}
import org.apache.http.entity.mime.{HttpMultipartMode, MultipartEntityBuilder}
import org.apache.http.impl.client.HttpClientBuilder

import scala.concurrent.duration._
import scala.sys.process._

object WebServer {
  def main(args: Array[String]) {

    implicit val system = ActorSystem("my-system")
    implicit val materializer = ActorMaterializer()
    // needed for the future flatMap/onComplete in the end
    implicit val executionContext = system.dispatcher

    def tempDestination(fileInfo: FileInfo): File = {
      val extension = FilenameUtils.getExtension(fileInfo.getFileName)
      File.createTempFile(randomUUID().toString, "." + extension)
    }


    val route =
      post {
        withSizeLimit(104857600L) {
          pathPrefix("grayscale") {
            pathEndOrSingleSlash {
              storeUploadedFile("file", tempDestination) {
                case (_, file) =>
                  println("test")
                  val oldFile = file.getAbsolutePath
                  val newFile = FilenameUtils.getFullPath(file.getAbsolutePath) + FilenameUtils.getBaseName(file.getAbsolutePath) + "_grayscale." + FilenameUtils.getExtension(file.getAbsolutePath)
                  val convert = "convert " + oldFile + " -colorspace Gray " + newFile
                  (convert).!!
                  val chunked = HttpEntity(ContentTypes.NoContentType, FileIO.fromPath(Paths.get(s"$newFile"), 100000))
                  complete(chunked)
              }
            } ~
              path("resize") {
                toStrictEntity(3 seconds, Long.MaxValue) {
                  formFields('size) { (size) =>
                    storeUploadedFile("file", tempDestination) {
                      case (_, file) =>
                        println("return")
                        val oldFile = file.getAbsolutePath
                        val newFile = FilenameUtils.getFullPath(file.getAbsolutePath) + FilenameUtils.getBaseName(file.getAbsolutePath) + "_grayscale." + FilenameUtils.getExtension(file.getAbsolutePath)
                        val convert = "convert " + oldFile + " -colorspace Gray " + newFile
                        (convert).!!
                        val byteArray = resizeGrayscaleImage(new File(newFile), size)
                        val chunked = HttpEntity(ContentTypes.NoContentType,byteArray)
                        println("return")
                        complete(chunked)
                    }
                  }
                }
              }
          }
        }
      }


    val bindingFuture = Http().bindAndHandle(route, "0.0.0.0", 8081)


  }


  def resizeGrayscaleImage(grayscaleImage: File, size: String): Array[Byte] = {
    val client = HttpClientBuilder.create.build
    val post = new HttpPost("http://resize:8083/resize")
    val fileBody = new FileBody(grayscaleImage, ContentType.DEFAULT_BINARY)
    val sizeStringBody = new StringBody(size, ContentType.MULTIPART_FORM_DATA)
    val builder = MultipartEntityBuilder.create
    builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE)
    builder.addPart("file", fileBody)
    builder.addPart("size", sizeStringBody)
    val entity = builder.build
    post.setEntity(entity)
    try {
      val response = client.execute(post)
      return IOUtils.toByteArray(response.getEntity.getContent)
    } catch {
      case e: IOException =>
        e.printStackTrace()
    }
    new Array[Byte](0)
  }

}