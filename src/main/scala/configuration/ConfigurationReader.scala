package configuration

trait ConfigurationReader {
  def getConfigOption(key: String): Option[String]
}

object SystemPropertyReader extends ConfigurationReader {
  def getConfigOption(key: String): Option[String] = Option(System.getProperty(key))
}
