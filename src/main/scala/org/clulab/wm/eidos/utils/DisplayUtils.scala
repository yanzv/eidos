package org.clulab.wm.eidos.utils

import java.io.PrintWriter
import org.clulab.odin._
import org.clulab.processors.{Document, Sentence}
import org.clulab.wm.eidos.context.GeoNormFinder

import scala.runtime.ZippedTraversable3.zippedTraversable3ToTraversable
import org.clulab.wm.eidos.context.GeoPhraseID
import org.clulab.wm.eidos.context.TimEx
import org.clulab.wm.eidos.context.TimeNormFinder
import org.clulab.wm.eidos.groundings.grounders.EidosOntologyGrounder
import org.clulab.wm.eidos.mentions.EidosMention

trait GroundingInfoSupplier {
  def supplyGroundingInfo(m: EidosMention): String
}

object DisplayUtils {
  protected val nl = "\n"
  protected val tab = "\t"

  def eidosMentionsToDisplayString(
    eidosMentions: Seq[EidosMention],
    doc: Document,
    printDeps: Boolean = false,
    groundingInfoSupplierOpt: Option[GroundingInfoSupplier] = None
  ): String = {
    val mentions = eidosMentions.map(_.odinMention)
    val sb = new StringBuffer()
    val times = TimeNormFinder.getTimExs(mentions, doc.sentences)
    val locations = GeoNormFinder.getGeoPhraseIDs(mentions, doc.sentences)
    // val mentionsBySentence = mentions groupBy (_.sentence) mapValues (_.sortBy(_.start)) withDefaultValue Nil
    val eidosMentionsBySentence = eidosMentions groupBy (_.odinMention.sentence) mapValues (_.sortBy(_.odinMention.start)) withDefaultValue Nil
    for ((s, i) <- doc.sentences.zipWithIndex) {
      sb.append(s"sentence #$i $nl")
      sb.append(s.getSentenceText + nl)
      sb.append("Tokens: " + (s.words.indices, s.words, s.tags.get).zipped.mkString(", ") + nl)
      if (printDeps) sb.append(syntacticDependenciesToString(s) + nl)
      sb.append(nl)

      if (times(i).nonEmpty)
        sb.append("timeExpressions:" + nl + displayTimeExpressions(times(i)) + nl)
      if (locations(i).nonEmpty)
        sb.append("locationExpressions:" + nl + displayLocationExpressions(locations(i)) + nl)

      val sortedEidosMentions = eidosMentionsBySentence(i).sortBy(_.odinMention.label)
      val (eidosEvents, eidosEntities) = sortedEidosMentions.partition(_.odinMention matches "Event")
      val (eidosTbs, eidosRels) = eidosEntities.partition(_.odinMention.isInstanceOf[TextBoundMention])
      val sortedEidosEntities = eidosTbs ++ eidosRels.sortBy(_.odinMention.label)
      sb.append(s"entities: $nl")
      sortedEidosEntities.foreach(e => sb.append(s"${eidosMentionToDisplayString(e, groundingInfoSupplierOpt)} $nl"))

      sb.append(nl)
      sb.append(s"events: $nl")
      eidosEvents.foreach(e => sb.append(s"${eidosMentionToDisplayString(e, groundingInfoSupplierOpt)} $nl"))
      sb.append(s"${"=" * 50} $nl")
    }
    sb.toString
  }

  def mentionsToDisplayString(
    mentions: Seq[Mention],
    doc: Document,
    printDeps: Boolean = false
  ): String = {

    val sb = new StringBuffer()
    val times = TimeNormFinder.getTimExs(mentions, doc.sentences)
    val locations = GeoNormFinder.getGeoPhraseIDs(mentions, doc.sentences)
    val mentionsBySentence = mentions groupBy (_.sentence) mapValues (_.sortBy(_.start)) withDefaultValue Nil
    for ((s, i) <- doc.sentences.zipWithIndex) {
      sb.append(s"sentence #$i $nl")
      sb.append(s.getSentenceText + nl)
      sb.append("Tokens: " + (s.words.indices, s.words, s.tags.get).zipped.mkString(", ") + nl)
      if (printDeps) sb.append(syntacticDependenciesToString(s) + nl)
      sb.append(nl)

      if (times(i).nonEmpty)
        sb.append("timeExpressions:" + nl + displayTimeExpressions(times(i)) + nl)
      if (locations(i).nonEmpty)
        sb.append("locationExpressions:" + nl + displayLocationExpressions(locations(i)) + nl)

      val sortedMentions = mentionsBySentence(i).sortBy(_.label)
      val (events, entities) = sortedMentions.partition(_ matches "Event")
      val (tbs, rels) = entities.partition(_.isInstanceOf[TextBoundMention])
      val sortedEntities = tbs ++ rels.sortBy(_.label)
      sb.append(s"entities: $nl")
      sortedEntities.foreach(e => sb.append(s"${mentionToDisplayString(e)} $nl"))

      sb.append(nl)
      sb.append(s"events: $nl")
      events.foreach(e => sb.append(s"${mentionToDisplayString(e)} $nl"))
      sb.append(s"${"=" * 50} $nl")
    }
    sb.toString
  }

  def displayTimeExpressions(timexes: Seq[TimEx]): String = {
    val sb = new StringBuffer()
    for (timex <- timexes) {
      sb.append(s"$tab span: ${timex.span.start},${timex.span.end} $nl")
      for (i <- timex.intervals) {
        val start = i.startDate.toString
        val end = i.endDate.toString

        sb.append(s"$tab start: $start $nl")
        sb.append(s"$tab end: $end $nl")
      }
      sb.append(nl)
    }
    sb.toString
  }

  def displayLocationExpressions(geolocations: Seq[GeoPhraseID]): String = {
    val sb = new StringBuffer()
    for (location <- geolocations) {
      val geonameID = location.geonameID.getOrElse("Undef")

      sb.append(s"$tab span: ${location.startOffset},${location.endOffset} $nl")
      sb.append(s"$tab geoNameID: $geonameID$nl")

      /*
      for (i <- location.geolocations) {
        sb.append(s"$tab start: ${i._1} $nl")
        sb.append(s"$tab end: ${i._2} $nl")
        sb.append(s"$tab duration: ${i._3} $nl")
      }
      */
      sb.append(nl)
    }
    sb.toString
  }

  def eidosMentionToDisplayString(eidosMention: EidosMention, groundingInfoSupplierOpt: Option[GroundingInfoSupplier] = None): String = {
    val sb = new StringBuffer()
    val boundary = s"$tab${"-" * 30} $nl"

    def formatSection(section: String): Unit = {
      val lines = section.split('\n')

      sb.append(boundary)
      lines.foreach { line =>
        sb.append(s"${tab}$line $nl")
      }
    }

    val mention = eidosMention.odinMention
    sb.append(s"${mention.labels} => ${mention.text} $nl")
    sb.append(boundary)
    sb.append(s"${tab}Rule => ${mention.foundBy} $nl")
    val mentionType = mention.getClass.toString.split("""\.""").last
    sb.append(s"${tab}Type => $mentionType $nl")
    sb.append(boundary)
    mention match {
      case tb: TextBoundMention =>
        sb.append(s"${tab}${tb.labels.mkString(", ")} => ${tb.text} $nl")
        if (tb.attachments.nonEmpty) sb.append(s"${tab} * Attachments: ${attachmentsString(tb.attachments)} $nl")
      case em: EventMention =>
        sb.append(s"${tab}trigger => ${em.trigger.text} $nl")
        if (em.trigger.attachments.nonEmpty) sb.append(s"${tab} * Attachments: ${attachmentsString(em.trigger.attachments)} $nl")
        sb.append(argumentsToString(em, nl, tab))
        if (em.attachments.nonEmpty) {
          sb.append(s"${tab}Event Attachments: ${attachmentsString(em.attachments)} $nl")
        }
      case rel: RelationMention =>
        sb.append(argumentsToString(rel, nl, tab))
        if (rel.attachments.nonEmpty) {
          sb.append(s"${tab}Relation Attachments: ${attachmentsString(rel.attachments)} $nl")
        }
      case cs: CrossSentenceMention =>
        sb.append(argumentsToString(cs, nl, tab))
        if (cs.attachments.nonEmpty) {
          sb.append(s"${tab}CrossSentence Attachments: ${attachmentsString(cs.attachments)} $nl")
        }
      case _ => ()
    }
    val groundingsStringOpt = GroundingUtils.getGroundingsStringOpt(eidosMention, EidosOntologyGrounder.PRIMARY_NAMESPACE, delim = nl + nl)
    groundingsStringOpt.foreach { groundingsString =>
      // There can be a grounding that is empty, maybe because the slots aren't right.
      //if (groundingsString.nonEmpty) {
        formatSection(groundingsString)
      //}
    }
    groundingInfoSupplierOpt.foreach { groundingInfoSupplier =>
      val groundingInfo = groundingInfoSupplier.supplyGroundingInfo(eidosMention)
      formatSection(groundingInfo)
    }
    sb.append(s"$boundary $nl")
    sb.toString
  }

  def mentionToDisplayString(mention: Mention): String = {
    val sb = new StringBuffer()
    val boundary = s"$tab ${"-" * 30} $nl"
    sb.append(s"${mention.labels} => ${mention.text} $nl")
    sb.append(boundary)
    sb.append(s"$tab Rule => ${mention.foundBy} $nl")
    val mentionType = mention.getClass.toString.split("""\.""").last
    sb.append(s"$tab Type => $mentionType $nl")
    sb.append(boundary)
    mention match {
      case tb: TextBoundMention =>
        sb.append(s"$tab ${tb.labels.mkString(", ")} => ${tb.text} $nl")
        if (tb.attachments.nonEmpty) sb.append(s"$tab  * Attachments: ${attachmentsString(tb.attachments)} $nl")
      case em: EventMention =>
        sb.append(s"$tab trigger => ${em.trigger.text} $nl")
        if (em.trigger.attachments.nonEmpty) sb.append(s"$tab  * Attachments: ${attachmentsString(em.trigger.attachments)} $nl")
        sb.append(argumentsToString(em, nl, tab) + nl)
        if (em.attachments.nonEmpty) {
          sb.append(s"$tab Event Attachments: ${attachmentsString(em.attachments)} $nl")
        }
      case rel: RelationMention =>
        sb.append(argumentsToString(rel, nl, tab) + nl)
        if (rel.attachments.nonEmpty) {
          sb.append(s"$tab Relation Attachments: ${attachmentsString(rel.attachments)} $nl")
        }
      case cs: CrossSentenceMention =>
        sb.append(argumentsToString(cs, nl, tab) + nl)
        if (cs.attachments.nonEmpty) {
          sb.append(s"$tab CrossSentence Attachments: ${attachmentsString(cs.attachments)} $nl")
        }
      case _ => ()
    }
    sb.append(s"$boundary $nl")
    sb.toString
  }

  def argumentsToString(b: Mention, nl: String, tab: String): String = {
    val sb = new StringBuffer
    b.arguments foreach {
      case (argName, ms) =>
        ms foreach { v =>
          sb.append(s"${tab}$argName ${v.labels.mkString("(", ", ", ")")} => ${v.text} $nl")
          if (v.attachments.nonEmpty) sb.append(s"$tab  * Attachments: ${attachmentsString(v.attachments)} $nl")
        }
    }
    sb.toString
  }

  def attachmentsString(mods: Set[Attachment]): String = s"${mods.mkString(", ")}"

  def syntacticDependenciesToString(s:Sentence): String = {
    if (s.dependencies.isDefined) {
      s.dependencies.get.toString
    } else "[Dependencies not defined]"
  }


  /* Wrappers for displaying the mention string */
  def displayMentions(mentions: Seq[Mention], doc: Document, printDeps: Boolean = false): Unit = {
    println(mentionsToDisplayString(mentions, doc, printDeps))
  }

  def displayEidosMentions(eidosMentions: Seq[EidosMention], doc: Document, printDeps: Boolean = false,
      groundingInfoSupplierOpt: Option[GroundingInfoSupplier] = None): Unit = {
    println(eidosMentionsToDisplayString(eidosMentions, doc, printDeps, groundingInfoSupplierOpt))
  }

  def displayMention(mention: Mention): Unit = println(mentionToDisplayString(mention))

  def displayEidosMention(eidosMention: EidosMention): Unit = println(eidosMentionToDisplayString(eidosMention))

  def shortDisplay(m: Mention): Unit = {
    println(s"${m.label}: [${m.text}] + ${m.attachments.mkString(",")}")
    if (m.isInstanceOf[EventMention]) {
      for (arg <- m.arguments) {
        println(s"\t${arg._1}: ${arg._2.map(am => am.text + " " + am.attachments.mkString(",")).mkString("; ")}")
      }
    }
  }

  /* Wrappers for printing the mention string to a file */
  def printMentions(mentions: Seq[Mention], doc: Document, pw: PrintWriter, printDeps: Boolean = false): Unit = {
    pw.println(mentionsToDisplayString(mentions, doc, printDeps))
  }

  def printMention(mention: Mention, pw: PrintWriter): Unit = pw.println(mentionToDisplayString(mention))

  def webAppMention(mention: Mention): String =
      xml.Utility.escape(mentionToDisplayString(mention))
          .replaceAll(nl, "<br>")
          .replaceAll(tab, htmlTab)

  def webAppEidosMention(eidosMention: EidosMention): String =
    xml.Utility.escape(eidosMentionToDisplayString(eidosMention))
      .replaceAll(nl, "<br>")
      .replaceAll(tab, htmlTab)

  def htmlTab: String = "&nbsp;&nbsp;&nbsp;&nbsp;"

  def webAppTimeExpressions(intervals: Seq[TimEx]): String =
      xml.Utility.escape(displayTimeExpressions(intervals))
          .replaceAll(nl, "<br>")
          .replaceAll(tab, "&nbsp;&nbsp;&nbsp;&nbsp;")

  def webAppGeoLocations(locations: Seq[GeoPhraseID]): String =
    xml.Utility.escape(displayLocationExpressions(locations))
      .replaceAll(nl, "<br>")
      .replaceAll(tab, "&nbsp;&nbsp;&nbsp;&nbsp;")
  
}
