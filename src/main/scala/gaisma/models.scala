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

  def parse: LocationData = {
    val document = Jsoup.parse(this.htmlBody)
    val worldLocation = document.select(".hdr+ small a").asScala.map(_.text())
    val title = document.title()

    LocationData(
      name = title.takeWhile(_ != ','),
      worldLocation = worldLocation,
      sunlightTable = sunlightFromTableRows(document.select("#future-days-table tr"))
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

case class LocationData(name: String, worldLocation: Seq[String], sunlightTable: Map[Any, Any]) extends StatsEntity {
  override def toString: String = s"${name} (${worldLocation.mkString(" -> ")})"
}
