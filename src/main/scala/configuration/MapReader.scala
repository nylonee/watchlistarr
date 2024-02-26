package configuration

class MapAndFileAndSystemPropertyReader(map: Map[String, String]) extends ConfigurationReader {
  override def getConfigOption(key: String): Option[String] = map.get(key) match {
    case r@Some(_) => r
    case None => FileAndSystemPropertyReader.getConfigOption(key)
  }
}
