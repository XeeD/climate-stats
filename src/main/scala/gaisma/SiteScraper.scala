package gaisma

import akka.NotUsed
import akka.actor.Actor
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import akka.stream.{ActorMaterializer, FlowShape, OverflowStrategy}
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
      .via(regionFeedbackFlow)
      .mapAsyncUnordered(20)(fetch(_)(GaismaLocationDocument(_, _)))
      .map(_.parse).async
      .runForeach(location => log.info(s"Result ${location}"))
  }

  val regionFeedbackFlow = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val merge =
      builder.add(Merge[GaismaRegion](2))

    val partition =
      builder.add(Partition[(String, String)](2, link =>
      if (GaismaLocation.isLocationPath(link._1)) 0 else 1)
    )

    val fetch =
      builder.add(Flow[GaismaRegion].mapAsyncUnordered(2)(region =>
        fetchSimple(region).map(htmlBody => GaismaRegionDocument(region, htmlBody))
      ))

    val parse =
      builder.add(Flow[GaismaRegionDocument].map(_.parse).async)

    val mapConcat =
      builder.add(Flow[RegionData].mapConcat(_.links))

    val toLocation =
      builder.add(Flow[(String, String)].map({ case (path, name) => GaismaLocation(path, name) }))

    val toRegion =
      builder.add(Flow[(String, String)].map({ case (path, name) => GaismaRegion(path, name) }))

    val buffer =
      builder.add(Flow[GaismaRegion].buffer(1000, OverflowStrategy.fail))

    merge ~> fetch ~> parse ~> mapConcat ~> partition ~> toLocation
    merge <~ buffer <~ toRegion          <~ partition

    FlowShape(merge.in(1), toLocation.out)
  }

  def fetchSimple(pageDescriptor: GaismaPageDescriptor): Future[String] = {
    log.debug(s"Fetching ${pageDescriptor} at ${pageDescriptor.toUri}")
    http.singleRequest(HttpRequest(uri = pageDescriptor.toUri))
      .flatMap({
        case HttpResponse(StatusCodes.OK, _, entity, _) =>
          log.debug(s"Fetched ${pageDescriptor} at ${pageDescriptor.toUri}")
          entity.dataBytes.runFold(ByteString(""))(_ ++ _)
            .map(_.decodeString("UTF-8"))

        case resp@HttpResponse(code, _, _, _) =>
          log.error(s"Fetch failed for ${pageDescriptor} at ${pageDescriptor.toUri}")
          resp.discardEntityBytes()
          Future.failed(
            new RuntimeException(s"Error when fetching ${pageDescriptor}: HTTP ${code.toString}")
          )
      })
  }

  def fetch[I <: GaismaPageDescriptor, O <: GaismaDocument[StatsEntity]](pageDescriptor: I)
    (transformer: (I, String) => O): Future[O] = {
    fetchSimple(pageDescriptor).map(transformer(pageDescriptor, _))
  }
}
