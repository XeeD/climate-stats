package gaisma

import akka.actor.{ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.{ExecutionContext, Future}

class GaismaService(implicit system: ActorSystem, implicit val timeout: Timeout) {
  implicit val ec: ExecutionContext = system.dispatcher
  val cityScraperActor = system.actorOf(Props[LocationScraper])
  val siteScraperActor = system.actorOf(Props[SiteScraper])
  val locationStoreActor = system.actorOf(Props[LocationStore])

  def scrapeCity(url: String, name: String): Future[String] = {
    val city = GaismaLocation(url, name)
    (cityScraperActor ? city).map({
      case r: LocationData => r.toString
      case x =>
        println(x.toString())
        s"Unknown data received: ${x.toString}"
    })
  }

  def getLocation(locationId: String): Future[String] = {
    (locationStoreActor ? RetrieveLocation(locationId))
      .map({
        case Some(result) => result match {
          case locationData: LocationData => locationData.toString
        }
        case None => "Not found"
      })
  }

  def scrapeSite(): String = {
    siteScraperActor ! ScrapeSite(locationStoreActor)
    "Scraping started"
  }
}
