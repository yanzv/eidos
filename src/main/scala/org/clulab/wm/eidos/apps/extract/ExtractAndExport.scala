package org.clulab.wm.eidos.apps.extract

import org.clulab.wm.eidos.EidosApp
import org.clulab.wm.eidos.EidosSystem
import org.clulab.wm.eidos.exporters.Exporter
import org.clulab.wm.eidos.groundings.grounders.CompositionalGrounder
import org.clulab.wm.eidoscommon.utils.FileUtils

/**
  * App used to extract mentions from files in a directory and produce the desired output format (i.e., jsonld, mitre
  * tsv or serialized mentions).  The input and output directories as well as the desired export formats are specified
  * in eidos.conf (located in src/main/resources).
  */
object ExtractAndExport extends EidosApp {
  val inputDir = getArgString("apps.inputDirectory", None)
  val outputDir = getArgString("apps.outputDirectory", None)
  val inputExtension = getArgString("apps.inputFileExtension", None)
  val exportAs = getArgStrings("apps.exportAs", None)
  val groundAs = getArgStrings("apps.groundAs", None)
  val groundedAs = groundAs.flatMap { grounder =>
    grounder match {
      case "wm_compositional" =>
        CompositionalGrounder.branches.map { branch => grounder + "/" + branch }
      case other => Seq(other)
    }
  }

  val topN = getArgInt("apps.groundTopN", Some(5))
  val files = FileUtils.findFiles(inputDir, inputExtension)
  val reader = new EidosSystem()

  // For each file in the input directory:
  files.par.foreach { file =>
    // 1. Open corresponding output file and make all desired exporters
    println(s"Extracting from ${file.getName}")
    // 2. Get the input file contents
    val text = FileUtils.getTextFromFile(file)
    // 3. Extract causal mentions from the text
    val annotatedDocument = reader.extractFromText(text, idOpt = Some(file.getName))
    // 4. Export to all desired formats
    exportAs.foreach { format =>
      Exporter(format, s"$outputDir/${file.getName}", reader, groundedAs, topN, config).export(annotatedDocument)
    }
  }
}
