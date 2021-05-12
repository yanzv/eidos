package org.clulab.wm.wmexchanger.wmconsumer

import org.apache.kafka.clients.consumer.{KafkaConsumer => ApacheKafkaConsumer}
import org.clulab.wm.eidoscommon.utils.Closer.AutoCloser
import org.clulab.wm.eidoscommon.utils.{FileEditor, FileUtils, Logging}
import org.clulab.wm.wmexchanger.utils.{Extensions, LockUtils}
import org.json4s._

import java.io.File
import java.time.Duration
import java.util.{Collections, ConcurrentModificationException, Properties}

class KafkaConsumer(appProperties: Properties, kafkaProperties: Properties, lock: Boolean = false)
    extends KafkaConsumerish {
  import org.clulab.wm.wmexchanger.wmconsumer.KafkaConsumer._
  implicit val formats: DefaultFormats.type = org.json4s.DefaultFormats

  logger.info("Opening consumer...")
  val topic: String = appProperties.getProperty("topic")
  val outputDir: String = appProperties.getProperty("output.dir")
  FileUtils.ensureDirsExist(outputDir)
  val closeDuration: Int = appProperties.getProperty("close.duration").toInt

  protected val consumer: ApacheKafkaConsumer[String, String] = {
    val consumer = new ApacheKafkaConsumer[String, String](kafkaProperties)

    consumer.subscribe(Collections.singletonList(topic))
    consumer
  }

  def poll(duration: Int): Unit = {
    if (lock)
      LockUtils.cleanupLocks(outputDir, Extensions.lock, Extensions.json)

    val records = consumer.poll(Duration.ofSeconds(duration))

    logger.info(s"Polling ${records.count} records...")
    records.forEach { record =>
      val key = record.key
      val value = record.value
      val file = FileEditor(new File(key + ".")).setDir(outputDir).setExt(Extensions.json).get
      logger.info("Consuming " + file.getName)

      FileUtils.printWriterFromFile(file).autoClose { printWriter =>
        printWriter.print(value)
      }
      // Now that the file is complete and closed, add a corresponding lock file.
      if (lock) {
        val lockFile = FileEditor(file).setExt(Extensions.lock).get
        lockFile.createNewFile()
      }
    }
  }

  def close(): Unit = {
    logger.info("Closing consumer...")
    try {
      consumer.close(Duration.ofSeconds(closeDuration))
    }
    catch {
      case _: ConcurrentModificationException => // KafkaConsumer is not safe for multi-threaded access
    }
  }
}

object KafkaConsumer extends Logging
