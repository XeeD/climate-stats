package gaisma

import akka.NotUsed
import akka.actor.Actor
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import akka.util.ByteString

import scala.concurrent.Future

class SiteScraper extends Actor {

  import context.dispatcher

  implicit val materializer = ActorMaterializer()
  val log = Logging(context.system, this)
  val http = Http(context.system)

  def receive = {
    case "scrapeSite" => scrapeSite()
  }

  def scrapeSite() = {
    Source.single(GaismaRegion("/dir/001-continent.html", "World"))
      .via(regionFlow)
      .via(regionFlow)
      .via(countryFlow)
      .mapAsync(6)(fetch(_)(GaismaLocationDocument(_, _)))
      .map(_.parse)
      .runForeach(location => log.info(s"Result ${location}"))
  }

  val regionScrapeFlow =
    Flow[GaismaRegion]
      .mapAsync(2)(fetch(_)(GaismaRegionDocument(_, _)))
      .map(_.parse)

  val regionFlow =
    regionScrapeFlow
      .mapConcat(_.links.map({ case (path, name) => GaismaRegion(path, name) }))

  val countryFlow =
    regionScrapeFlow
      .mapConcat(_.links.map({ case (path, name) => GaismaLocation(path, name) }))

  def fetch[I <: GaismaPageDescriptor, O <: GaismaDocument[StatsEntity]](pageDescriptor: I)
    (implicit transformer: (I, String) => O): Future[O] = {
    log.info(s"Fetching ${pageDescriptor} at ${pageDescriptor.toUri}")
    http.singleRequest(HttpRequest(uri = pageDescriptor.toUri))
      .flatMap({
        case HttpResponse(StatusCodes.OK, _, entity, _) =>
          log.info(s"Fetched ${pageDescriptor} at ${pageDescriptor.toUri}")
          entity.dataBytes.runFold(ByteString(""))(_ ++ _)
            .map(_.decodeString("UTF-8"))
            .map(transformer(pageDescriptor, _))

        case resp@HttpResponse(code, _, _, _) =>
          resp.discardEntityBytes()
          Future.failed(new RuntimeException(s"Error when fetching ${pageDescriptor}: HTTP ${code.toString}"))
      })
  }
}
