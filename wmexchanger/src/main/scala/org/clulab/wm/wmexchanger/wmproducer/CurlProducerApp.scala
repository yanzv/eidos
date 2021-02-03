package org.clulab.wm.wmexchanger.wmproducer

import com.typesafe.config.ConfigFactory
import org.clulab.wm.eidoscommon.utils.Closer.AutoCloser
import org.clulab.wm.eidoscommon.utils.Logging
import org.clulab.wm.eidoscommon.utils.{FileUtils, PropertiesBuilder, Sinker, StringUtils}

object CurlProducerApp extends App with Logging {
  val version = "0.2.3"

  val inputDir = args(0)
  val outputFile = args(1)

  val config = ConfigFactory.load("curlproducer")
  val service = config.getString("CurlProducerApp.service")
  val login = config.getString("CurlProducerApp.login")
  val properties = PropertiesBuilder.fromFile(login).get
  val username = properties.getProperty("username")
  val password = properties.getProperty("password")

  val files = FileUtils.findFiles(inputDir, "jsonld")

  Sinker.printWriterFromFile(outputFile).autoClose { printWriter =>
    files.foreach { file =>
      logger.info(s"Processing ${file.getName}")
      val docId = StringUtils.beforeFirst(file.getName, '.')
      try {
        val command = s"""curl
            |--basic
            |--user "$username:$password"
            |-X POST "$service"
            |-H "accept: application/json"
            |-H "Content-Type: multipart/form-data"
            |-F 'metadata={ "identity": "eidos", "version": "$version", "document_id": "$docId" }'
            |-F "file=@${file.getName}"
            |""".stripMargin.replace('\r', ' ').replace('\n', ' ')

        printWriter.print(command)
        printWriter.print("\n")
      }
      catch {
        case exception: Exception =>
          logger.error(s"Exception for file $file", exception)
      }
    }
  }
}
