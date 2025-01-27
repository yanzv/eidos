package org.clulab.wm.eidos.apps.batch

import org.clulab.utils.ThreadUtils
import org.clulab.wm.eidos.EidosOptions
import org.clulab.wm.eidos.EidosSystem
import org.clulab.wm.eidos.metadata.CluText
import org.clulab.wm.eidos.serialization.jsonld.JLDCorpus
import org.clulab.wm.eidoscommon.utils.Closer.AutoCloser
import org.clulab.wm.eidoscommon.utils.{FileEditor, FileUtils, Sourcer, StringUtils, Timer}
import org.clulab.wm.eidoscommon.utils.Logging
import org.json4s.DefaultFormats
import org.json4s.jackson.JsonMethods

import scala.collection.mutable

object ExtractCluMetaFromDirectoryWithId extends App with Logging {
  val inputDir = args(0)
  val metaDir = args(1)
  val outputDir = args(2)
  val timeFile = args(3)
  val mapFile = args(4)
  val threads = args(5).toInt

  val fileToIdMap = {
    implicit val formats: DefaultFormats.type = org.json4s.DefaultFormats

    val fileToIdMap = new mutable.HashMap[String, String]()

    Sourcer.sourceFromFile(mapFile).autoClose { source =>
      source.getLines().foreach { line =>
        val json = JsonMethods.parse(line)
        val filename = (json \ "file_name").extract[String]
        val id = (json \ "_id").extract[String]
        fileToIdMap += (filename -> id)
      }
    }
    fileToIdMap
  }

  val doneDir = inputDir + "/done"
  val textToMeta = CluText.convertTextToMeta _

  val files = FileUtils.findFiles(inputDir, "txt")
  val parFiles = ThreadUtils.parallelize(files, threads)

  Timer.time("Whole thing") {
    val timePrintWriter = FileUtils.appendingPrintWriterFromFile(timeFile)
    timePrintWriter.println("File\tSize\tTime")
    val timer = new Timer("Startup")

    timer.start()
    // Prime it first.  This counts on overall time, but should not be attributed
    // to any particular document.
    val reader = new EidosSystem()
    val options = EidosOptions()

    reader.extractFromText("This is a test.")
    timer.stop()

    timePrintWriter.println("Startup\t0\t" + timer.elapsedTime.get)

    parFiles.foreach { file =>
      try {
        logger.info(s"Extracting from ${file.getName}")
        val timer = new Timer("Single file in parallel")
        val size = timer.time {
          val id = {
            // These all and with .txt
            val baseFilename = StringUtils.beforeLast(file.getName, '.', true)
            val extensions = Array(".html", ".htm", ".pdf")

            def getId(extension: String): Option[String] =
              fileToIdMap.get(baseFilename + extension)

            val extensionIndex = extensions.indexWhere { extension: String =>
              getId(extension).isDefined
            }
            val id = if (extensionIndex >= 0)
              getId(extensions(extensionIndex))
            else
              fileToIdMap.get(baseFilename)

            if (id.isEmpty)
              println("This shouldn't happen!")
            id.get
          }

          // 1. Get the input file text and metadata
          val metafile = textToMeta(file, metaDir)
          val eidosText = CluText(reader, file, Some(metafile))
          val text = eidosText.getText
          val metadata = eidosText.getMetadata
          // 2. Extract causal mentions from the text
          val annotatedDocument = reader.extractFromText(text, options, metadata)
          // 3. Write to output file
          val path = CluText.convertTextToJsonld(file, outputDir)
          FileUtils.printWriterFromFile(path).autoClose { printWriter =>
            new JLDCorpus(annotatedDocument).serialize(printWriter)
          }
          // Now move the file to directory done
          val newFile = FileEditor(file).setDir(doneDir).get
          file.renameTo(newFile)
          text.length
        }
        this.synchronized {
          timePrintWriter.println(file.getName + "\t" + size + "\t" + timer.elapsedTime.get)
        }
      }
      catch {
        case exception: Exception =>
          logger.error(s"Exception for file $file", exception)
      }
    }
    timePrintWriter.close()
  }
}
