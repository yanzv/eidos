package org.clulab.wm.wmexchanger.wmconsumer

import org.clulab.wm.eidoscommon.utils.PropertiesBuilder
import org.clulab.wm.wmexchanger.utils.LoopApp
import org.clulab.wm.wmexchanger.utils.SafeThread
import org.clulab.wm.wmexchanger.utils.WmUserApp

import java.util.Properties

class KafkaConsumerLoopApp(args: Array[String]) extends WmUserApp(args,  "/kafkaconsumer.properties") {
  val localKafkaProperties: Properties = {
    // This allows the login to be contained in a file external to the project.
    val loginProperty = appProperties.getProperty("login")
    val loginPropertiesBuilder = PropertiesBuilder.fromFile(loginProperty)

    PropertiesBuilder(kafkaProperties).putAll(loginPropertiesBuilder).get
  }

  val topic: String = appProperties.getProperty("topic")
  val outputDir: String = appProperties.getProperty("outputDir")

  val pollDuration: Int = appProperties.getProperty("poll.duration").toInt
  val waitDuration: Long = appProperties.getProperty("wait.duration").toLong
  val closeDuration: Int = appProperties.getProperty("close.duration").toInt

  val thread: SafeThread = new SafeThread(KafkaConsumerApp.logger, interactive, waitDuration) {

    override def runSafely(): Unit = {
      // This is kept open the entire time, so time between pings is extra important.
      val consumer = new KafkaConsumer(localKafkaProperties, closeDuration, topic, outputDir, lock = true)
      // autoClose isn't executed if the thread is shot down, so this hook is used instead.
      sys.ShutdownHookThread { consumer.close() }

      while (!isInterrupted) {
        consumer.poll(pollDuration)
      }
      consumer.close()
    }
  }
}

object KafkaConsumerLoopApp extends App with LoopApp {
  loop {
    () => new KafkaConsumerApp(args).thread
  }
}