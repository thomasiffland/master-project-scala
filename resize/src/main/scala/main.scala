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
          pathPrefix("resize") {
            pathEndOrSingleSlash {
              toStrictEntity(3 seconds, Long.MaxValue) {
                formFields('size) { (size) =>
                  storeUploadedFile("file", tempDestination) {
                    case (_, file) =>
                      val oldFile = file.getAbsolutePath
                      val newFile = FilenameUtils.getFullPath(file.getAbsolutePath) + FilenameUtils.getBaseName(file.getAbsolutePath) + "_resized." + FilenameUtils.getExtension(file.getAbsolutePath)
                      val convert = "convert " + oldFile + " -resize " + size + " " + newFile
                      (convert).!!
                      val chunked = HttpEntity(ContentTypes.NoContentType, FileIO.fromPath(Paths.get(s"$newFile"), 100000))
                      complete(chunked)
                  }
                }
              }
            } ~
              path("percent") {
                toStrictEntity(3 seconds, Long.MaxValue) {
                  formFields('percent) { (percent) =>
                    storeUploadedFile("file", tempDestination) {
                      case (_, file) =>
                        val oldFile = file.getAbsolutePath
                        val newFile = FilenameUtils.getFullPath(file.getAbsolutePath) + FilenameUtils.getBaseName(file.getAbsolutePath) + "_resized." + FilenameUtils.getExtension(file.getAbsolutePath)
                        val sizeString = generateSizeString(file, percent)
                        val convert = "convert " + oldFile + " -resize " + sizeString + " " + newFile
                        (convert).!!
                        val chunked = HttpEntity(ContentTypes.NoContentType, FileIO.fromPath(Paths.get(s"$newFile"), 100000))
                        complete(chunked)
                    }
                  }
                }
              }

          }
        }
      }


    val bindingFuture = Http().bindAndHandle(route, "0.0.0.0", 8083)

  }

  def generateSizeString(jpg: File, percent: String): String = {
    val client = HttpClientBuilder.create.build
    val post = new HttpPost("http://exifdata:8082/exifdata/filtered")
    val fileBody = new FileBody(jpg, ContentType.DEFAULT_BINARY)
    val filterBody = new StringBody("Image Height", ContentType.DEFAULT_BINARY)
    val builder = MultipartEntityBuilder.create
    builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE)
    builder.addPart("file", fileBody)
    builder.addPart("filter", filterBody)
    val entity = builder.build
    post.setEntity(entity)
    try {
      val response = client.execute(post)
      val returnValue = new String(IOUtils.toByteArray(response.getEntity.getContent))
      val imageHeight = returnValue.split(":")(1).trim.toFloat
      val newImageHeight = imageHeight * (percent.toFloat / 100f)
      println(newImageHeight)
      return newImageHeight + "x" + newImageHeight
    } catch {
      case e: IOException =>
        e.printStackTrace()
    }
    new String()
  }
}