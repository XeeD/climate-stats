package gaisma

import gaisma.GaismaLocationDocument.sunlightFromTableRows
import org.jsoup.Jsoup

import scala.collection.JavaConverters._

sealed trait GaismaPageDescriptor {
  def toString: String

  def toUri: String
}

case class GaismaRegion(path: String, name: String) extends GaismaPageDescriptor {
  override def toString: String = s"Region ${name} (at ${path})"

  def toUri = Site.url(path)
}

case class GaismaLocation(path: String, name: String) extends GaismaPageDescriptor {
  override def toString: String = s"Location ${name} (at ${path})"

  def toUri = Site.url(path)
}

object GaismaLocation {
  def isLocationPath(path: String) = path.startsWith("/location/")
}

sealed trait GaismaDocument[+ParsedEntity <: StatsEntity] {
  def htmlBody: String

  def parse: ParsedEntity
}

case class GaismaRegionDocument(continent: GaismaRegion, htmlBody: String) extends GaismaDocument[RegionData] {
  def parse: RegionData = {
    val document = Jsoup.parse(htmlBody)
    val links = document.select("#a a").asScala.map(link =>
      (Site.extractPath(link.attr("href")), link.text())
    )
    RegionData(
      continent,
      links.toList
    )
  }
}

case class GaismaLocationDocument(location: GaismaLocation, htmlBody: String) extends GaismaDocument[LocationData] {

  val ExtractIdPattern = "pref-home-location=(.+)$".r.unanchored

  def parse: LocationData = {
    val document = Jsoup.parse(this.htmlBody)
    val worldLocation = document.select(".hdr+ small a").asScala.map(_.text())
    val title = document.title()

    val basicInformation = document
      .select("#basic-information+ .data div")
      .html()
      .split("<br>")
      .map(Jsoup.parseBodyFragment(_))
      .map(_.text().split(':').toList)
      .collect {
        case List(name, value) => name.trim -> value.trim
      }
      .toMap

    val id = document
      .selectFirst("#sun-data-tables+ .note a")
      .attr("href") match {
      case ExtractIdPattern(id) => id
      case _ => "Unknown"
    }

    LocationData(
      id = id,
      name = title.takeWhile(_ != ','),
      worldLocation = worldLocation,
      sunlightTable = sunlightFromTableRows(document.select("#future-days-table tr")),
      basicInformation = basicInformation
    )
  }
}

object GaismaLocationDocument {

  def sunlightFromTableRows(rows: Any) = {
    Map[Any, Any]()
  }
}

sealed trait StatsEntity

case class RegionData(region: GaismaRegion, links: collection.immutable.Iterable[(String, String)]) extends StatsEntity

case class LocationData(
  id: String,
  name: String,
  worldLocation: Seq[String],
  basicInformation: Map[String, String],
  sunlightTable: Map[Any, Any]) extends StatsEntity {

  override def toString: String = s"${id}: ${name} (${worldLocation.mkString(" -> ")})"

  def gpsInfo = {
    for {
      latitude <- basicInformation.get("Latitude")
      longitude <- basicInformation.get("Longitude")
      distance <- basicInformation.get("Distance")
    } yield s"Position ${latitude} ${longitude} (${distance}"
  }
}
