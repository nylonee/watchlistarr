package configuration

import org.slf4j.LoggerFactory
import org.yaml.snakeyaml.Yaml

import java.io.{File, FileInputStream}
import java.nio.file.{Files, Paths, StandardCopyOption}
import java.util
import scala.jdk.CollectionConverters.{ListHasAsScala, MapHasAsScala}

object FileAndSystemPropertyReader extends ConfigurationReader {

  private val logger = LoggerFactory.getLogger(getClass)

  private lazy val data: Map[String, String] = {
    val yaml       = new Yaml()
    val configFile = new File(SystemPropertyReader.getConfigOption("configPath").getOrElse(s"config/config.yaml"))

    try {
      // Ensure parent config folder exists
      val parentDir = configFile.getParentFile
      if (!parentDir.exists()) parentDir.mkdirs()

      if (!configFile.exists()) {
        val resourceStream = getClass.getClassLoader.getResourceAsStream("config-template.yaml")
        if (resourceStream != null) {
          try {
            Files.copy(resourceStream, Paths.get(configFile.toURI), StandardCopyOption.REPLACE_EXISTING)
            logger.info(s"Created config file in ${configFile.getPath}")
          } finally
            resourceStream.close()
        } else {
          logger.debug("config-template.yaml resource not found")
        }
      }

      if (configFile.exists()) {
        val inputStream = new FileInputStream(configFile)
        val result      = yaml.load[util.Map[String, Object]](inputStream).asScala
        inputStream.close()
        flattenYaml(Map.from(result))
      } else {
        Map.empty[String, String]
      }
    } catch {
      case e: Exception =>
        logger.debug(s"Failed to read from config.yaml: ${e.getMessage}")
        Map.empty[String, String]
    }
  }

  override def getConfigOption(key: String): Option[String] =
    if (data.contains(key))
      data.get(key)
    else
      SystemPropertyReader.getConfigOption(key)

  private def flattenYaml(yaml: Map[String, _]): Map[String, String] = yaml.flatMap {
    case (k, v: util.ArrayList[_]) =>
      List((k, v.asScala.mkString(",")))

    case (k, v: String) =>
      List((k, v))

    case (k, v: Integer) =>
      List((k, v.toString))

    case (k, v: java.lang.Boolean) =>
      List((k, v.toString))

    case (k, v: util.LinkedHashMap[String, _]) =>
      val flattenedInner = flattenYaml(Map.from(v.asScala))
      flattenedInner.map { case (innerK, innerV) =>
        (s"$k.$innerK", innerV)
      }.toList

    case (k, v) =>
      logger.warn(s"Unhandled config pair of type: ${k.getClass} -> ${v.getClass}")
      List((k, v.toString))
  }
}
