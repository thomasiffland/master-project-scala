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
import org.apache.http.entity.mime.content.FileBody
import org.apache.http.entity.mime.{HttpMultipartMode, MultipartEntityBuilder}
import org.apache.http.impl.client.HttpClientBuilder

import scala.sys.process._


object WebServer {
  def main(args: Array[String]) {

    implicit val system = ActorSystem("my-system")
    implicit val materializer = ActorMaterializer()
    // needed for the future flatMap/onComplete in the end
    implicit val executionContext = system.dispatchers.lookup("blocking-dispatcher")

    def tempDestination(fileInfo: FileInfo): File = {
      val extension = FilenameUtils.getExtension(fileInfo.getFileName)
      File.createTempFile(randomUUID().toString, "." + extension)
    }


    val route =
      post {
        withSizeLimit(104857600L) {
          pathPrefix("rawtojpg") {
            pathEndOrSingleSlash {
              storeUploadedFile("file", tempDestination) {
                case (_, file) =>
                  val oldFile = file.getAbsolutePath
                  val newFile = FilenameUtils.getFullPath(file.getAbsolutePath) + FilenameUtils.getBaseName(file.getAbsolutePath) + ".jpg"
                  val dcraw = "dcraw -c -w " + oldFile
                  val convert = "convert - " + newFile
                  (dcraw #| convert).!!
                  val chunked = HttpEntity(ContentTypes.NoContentType, FileIO.fromPath(Paths.get(s"$newFile"), 100000))
                  complete(chunked)
              }
            } ~
              path("grayscale") {
                storeUploadedFile("file", tempDestination) {
                  case (_, file) =>
                    val oldFile = file.getAbsolutePath
                    val newFile = FilenameUtils.getFullPath(file.getAbsolutePath) + FilenameUtils.getBaseName(file.getAbsolutePath) + ".jpg"
                    val dcraw = "dcraw -c -w " + oldFile
                    val convert = "convert - " + newFile
                    (dcraw #| convert).!!
                    val byteArray = greyscaleImage(new File(newFile))
                    val chunked = HttpEntity(ContentTypes.NoContentType, byteArray)
                    complete(chunked)
                }
              }
          }
        }
      }


    val bindingFuture = Http().bindAndHandle(route, "0.0.0.0", 8080)

  }

  def greyscaleImage(jpg: File): Array[Byte] = {
    val client = HttpClientBuilder.create.build
    val post = new HttpPost("http://localhost:8081/grayscale")
    val fileBody = new FileBody(jpg, ContentType.DEFAULT_BINARY)
    val builder = MultipartEntityBuilder.create
    builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE)
    builder.addPart("file", fileBody)
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