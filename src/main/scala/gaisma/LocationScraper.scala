package gaisma

import akka.actor.{Actor, Props}
import akka.pattern.ask
import akka.pattern.pipe
import akka.util.Timeout

import scala.concurrent.duration.DurationInt

class LocationScraper extends Actor {

  import context.dispatcher

  val locationFetcher = context.actorOf(Props[LocationFetcher])
  implicit val timeout = Timeout(5 seconds)

  def receive = {
    case location: GaismaLocation ⇒
      (locationFetcher ? location)
        .mapTo[GaismaLocationDocument]
        .flatMap(parse)
        .pipeTo(sender())
  }

  def parse(locationDocument: GaismaLocationDocument) = {
    val locationParser = context.actorOf(Props[Parser])
    val f = (locationParser ? locationDocument)
    f.onComplete(_ => context.stop(locationParser))
    f
  }
}
