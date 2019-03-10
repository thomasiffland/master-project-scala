import java.io.File
import java.nio.file.Paths
import java.util.UUID.randomUUID

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.FileInfo
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.FileIO
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
        pathPrefix("timelapse") {
          toStrictEntity(3 seconds,Long.MaxValue) {
            formFields('framerate) { (framerate) =>
              storeUploadedFile("file", tempDestination) {
                case (_, file) =>
                  val oldFile = file.getAbsolutePath
                  val folderToExtractTo = FilenameUtils.getFullPathNoEndSeparator(oldFile) + "/" + FilenameUtils.getBaseName(oldFile)
                  val timelapse = FilenameUtils.getFullPathNoEndSeparator(oldFile) + "/" + FilenameUtils.getBaseName(oldFile) + "/timelapse.mp4"
                  val folderFileHandle = new File(folderToExtractTo)
                  folderFileHandle.mkdirs()
                  Process(Seq("unzip", oldFile, "-d", folderToExtractTo), folderFileHandle).!!
                  Process(Seq("ffmpeg", "-r", framerate, "-pattern_type", "glob", "-i", "*.png", "-vcodec", "libx264", "timelapse.mp4"), folderFileHandle).!!
                  val chunked = HttpEntity(ContentTypes.NoContentType, FileIO.fromPath(Paths.get(s"$timelapse"), 100000))
                  complete(chunked)
              }
            }
          }
        }
      }


    val bindingFuture = Http().bindAndHandle(route, "0.0.0.0", 8084)

  }
}