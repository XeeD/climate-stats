package gaisma

import akka.actor.Actor
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import akka.pattern.pipe
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import akka.util.ByteString

import scala.concurrent.Future

class LocationFetcher extends Actor {

  import context.dispatcher

  val log = Logging(context.system, this)
  val http = Http(context.system)
  final implicit val materializer: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(context.system))

  def receive = {
    case pageDescriptor: GaismaLocation =>
      execute(pageDescriptor)(locationTransformer).pipeTo(sender())
  }

  def execute[I <: GaismaPageDescriptor, O <: GaismaDocument[StatsEntity]](pageDescriptor: I)
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

  implicit val locationTransformer = (location: GaismaLocation, htmlBody: String) => {
    new GaismaLocationDocument(location, htmlBody)
  }
}