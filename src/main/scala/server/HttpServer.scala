package server

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import akka.util.Timeout
import scala.concurrent.duration.DurationInt

import scala.io.StdIn
import gaisma._


object HttpServer {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher
  implicit val timeout: Timeout = Timeout(10 seconds)

  val gaismaService = new GaismaService

  val route =
    path("hello") {
      get {
        complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h1>Say hello to akka-http</h1>"))
      }
    } ~
      path("test-city-scrape") {
        get {
          complete(gaismaService.scrapeCity("/location/gibraltar.html", "Gibraltar"))
        }
      } ~
      path("scrape-site") {
        get {
          complete(gaismaService.scrapeSite())
        }
      } ~
      path("location" / Segment) { locationId =>
        get {
          complete(gaismaService.getLocation(locationId))
        }
      }

  val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)

  def main(args: Array[String]): Unit = {
    println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }
}

object Scraper
