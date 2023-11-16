package configuration

trait ConfigurationReader {
  def getConfigOption(key: String): Option[String]
}
