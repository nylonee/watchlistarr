package configuration

object SystemPropertyReader extends ConfigurationReader {
  override def getConfigOption(key: String): Option[String] = Option(System.getProperty(key))
}
