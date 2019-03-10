import java.io.File
import java.util.UUID.randomUUID

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.FileInfo
import akka.stream.ActorMaterializer
import org.apache.commons.io.FilenameUtils

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
        pathPrefix("exifdata") {
          pathEndOrSingleSlash {
            storeUploadedFile("file", tempDestination) {
              case (metadata, file) =>
                val oldFile = file.getAbsolutePath
                val exiftool = "exiftool " + oldFile
                val res = (exiftool).!!
                complete(res)
            }
          } ~
          path("filtered") {
            toStrictEntity(3 seconds, Long.MaxValue) {
              formFields('filter) { (filter) =>
                storeUploadedFile("file", tempDestination) {
                  case (metadata, file) =>
                    println(filter)
                    val oldFile = file.getAbsolutePath
                    val exiftool = "exiftool " + oldFile
                    val grep = Seq("grep", filter)
                    val res = (exiftool #| grep).!!
                    complete(res)
                }
              }
            }
          }
        }
      }


    val bindingFuture = Http().bindAndHandle(route, "0.0.0.0", 8082)

  }
}