package gaisma

object Site {
  def extractPath(siteUrl: String) = siteUrl.replace(s"${baseUrl}/${locale}/", "/")

  val baseUrl = "https://www.gaisma.com"
  val locale = "en"

  def url(path: String) = s"${baseUrl}/${locale}${path}"
}
