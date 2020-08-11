package org.clulab.wm.eidos.context

import java.time.temporal.ChronoField.MONTH_OF_YEAR

import ai.lum.common.ConfigUtils._
import com.typesafe.config.Config
import org.yaml.snakeyaml.Yaml
import org.clulab.timenorm.scate._
import org.clulab.odin.{Mention, State, TextBoundMention}
import org.clulab.processors.Document
import org.clulab.struct.{Interval => TextInterval}
import org.clulab.wm.eidos.attachments.{Location => LocationAttachment, Time => TimeAttachment}
import org.clulab.wm.eidos.extraction.Finder
import org.clulab.wm.eidos.utils.FileUtils.getTextFromResource

import scala.math.max
import scala.collection.JavaConverters._


case class SeasonMention(firstToken: Int, lastToken: Int, season: Map[String, Int], timeInterval: TimeStep)

object SeasonFinder {

  // SeasonFinder needs two data structures:
  //    - A Set of triggers that are used to find possible season mentions, e.g. "season", "meher"
  //    - A Map (key = GeoNameID) of Maps (key = season type) of Maps (key = star month & end month). For example,
  //      the planting season in Afar starts in June and ends in September:
  //          * "444179" -> "planting" -> ("start" -> 6, "end" -> 9)
  def fromConfig(config: Config): SeasonFinder = {
    val seasonDBPath: String = config[String]("seasonsDBPath")
    val seasonDBTxt = getTextFromResource(seasonDBPath)
    val yaml = new Yaml()
    val yamlDB = yaml.loadAll(seasonDBTxt).iterator()
    val seasonJavaTriggers = yamlDB.next().asInstanceOf[java.util.Map[String, java.util.ArrayList[String]]]
    val seasonTriggers = seasonJavaTriggers.get("triggers").asScala.toSet
    val seasonJavaMap = yamlDB.next().asInstanceOf[java.util.Map[String, java.util.Map[String, java.util.Map[String, Int]]]]
    val seasonMap = seasonJavaMap.asScala.mapValues(_.asScala.mapValues(_.asScala.toMap).toMap).toMap
    new SeasonFinder(seasonMap, seasonTriggers)
  }
}


class SeasonFinder(seasonMap: Map[String, Map[String, Map[String, Int]]], triggers: Set[String]) extends Finder{

  // This function is used to filter out mentions with no Time or Location attachments
  private def filterMentions(mention: (Int, Mention)): Boolean = {
    mention._2.label match {
      case l if l.equals("Time") || l.equals("Location") =>
        if (mention._2.attachments.isEmpty)
          false
        else
          mention._2.attachments.head match {
            case a: LocationAttachment if a.geoPhraseID.geonameID.isDefined => true
            case a: TimeAttachment if a.interval.intervals.nonEmpty => true
            case _ => false
          }
      case _ => false
    }
  }

  private def createMention(seasonMatch: (String, Int), sentIdx: Int, lemmas: Array[String], initialState: State): Option[SeasonMention] = {
    val seasonTrigger = seasonMatch._1
    val seasonTokenIdx = seasonMatch._2
    val sentSize = lemmas.length

    // Take all the mentions before and after the season trigger in the same sentence and all the mentions
    // in the previous 5 sentences. Sort them by their proximity to the trigger prioritizing same sentence:
    //    - Mentions in same sentence: Distance in tokens to the trigger
    //    - Mentions in previous sentences: Just their index in the reverse list + trigger's sentence length
    val previousMentions = initialState.mentionsFor(sentIdx, 0 until seasonTokenIdx).map(m => (seasonTokenIdx - m.tokenInterval.start, m))
    val followingMentions = initialState.mentionsFor(sentIdx, seasonTokenIdx + 1 until sentSize).map(m => (m.tokenInterval.start - seasonTokenIdx, m))
    val previousSentencesMentions = (max(0, sentIdx - 5) until sentIdx).reverse.flatMap(initialState.mentionsFor).zipWithIndex.map(m => (sentSize + m._2, m._1))
    val sortedMentions = (previousMentions ++ followingMentions ++ previousSentencesMentions).filter(filterMentions).sortBy(_._1).map(_._2)

    // Find the closest normalized Location
    val geoLocation = sortedMentions.filter(_.label == "Location")
      .map(_.attachments.head).map(_.asInstanceOf[LocationAttachment])
      .find(m => seasonMap.contains(m.geoPhraseID.geonameID.get))

    // Find the closest normalized Time
    val timeInterval = sortedMentions.filter(_.label == "Time")
      .map(_.attachments.head).map(_.asInstanceOf[TimeAttachment].interval.intervals.headOption)
      .find(_.isDefined)

    // If we find both Location and Time normalized create a SeasonMention if the season type is in the
    // seasons Map for that Location. SeasonMention is created with the first and last tokens
    // of the mention, the starting and ending months of the season according to the seasons Map, and
    // the timeInterval of Time that will be used as reference to normalize the season mention.
    // The season type if checked with following priority:
    //    1- The trigger is also the type (Uni-gram season expression), e.g. "meher"
    //    2- Long format type (Tri-gram season expression), e.g. "short rainy season"
    //    3- Short format type (Bi-gram season expression), e.g. "rainy season"
    if (geoLocation.isDefined && timeInterval.isDefined) {
      val geoLocationID = geoLocation.get.geoPhraseID.geonameID.get
      seasonTrigger match {
        case trigger if seasonMap(geoLocationID).contains(trigger) =>
          Some(SeasonMention(seasonTokenIdx, seasonTokenIdx, seasonMap(geoLocationID)(trigger), timeInterval.get.head))
        case _ if seasonTokenIdx > 0 =>
          val seasonType = lemmas(seasonTokenIdx - 1)
          lemmas.lift(seasonTokenIdx - 2) match {
              case Some(lemma) if seasonMap(geoLocationID).contains(lemma + " " + seasonType) =>
                val seasonLongType = lemma + " " + seasonType
                Some(SeasonMention(seasonTokenIdx - 2, seasonTokenIdx, seasonMap(geoLocationID)(seasonLongType), timeInterval.get.head))
              case _ if seasonMap(geoLocationID).contains(seasonType) =>
                Some(SeasonMention(seasonTokenIdx - 1, seasonTokenIdx, seasonMap(geoLocationID)(seasonType), timeInterval.get.head))
              case _ => None
          }
      }
    } else {
      None
    }
  }

  def find(doc: Document, initialState: State): Seq[Mention] = {
    val Some(text) = doc.text // Document must have a text.
    // For every season trigger found in text, try to create a SeasonMention and if it is defined
    // normalize it and create a TimeAttachment.
    val mentions = for {(sentence, sentenceIndex) <- doc.sentences.zipWithIndex
                        lemmas = sentence.lemmas.get
                        m <- lemmas.zipWithIndex.filter(s => triggers.contains(s._1))
                        seasonMention = createMention(m, sentenceIndex, lemmas, initialState)
                        if seasonMention.isDefined
    } yield {
      val seasonStartOffset = sentence.startOffsets(seasonMention.get.firstToken)
      val seasonEndOffset = sentence.endOffsets(seasonMention.get.lastToken)
      val seasonStartMonth = seasonMention.get.season("start")
      val seasonEndMonth = seasonMention.get.season("end")

      // TextInterval for the TimEx
      val seasonTextInterval = TextInterval(seasonStartOffset, seasonEndOffset)
      val seasonText = text.substring(seasonStartOffset, seasonEndOffset)

      // Normalize the season using the Year of the Time mention found in closestMention as reference.
      // Create a TimEx for this season.
      val yearInterval = Year(seasonMention.get.timeInterval.startDate.getYear)
      val startInterval = ThisRI(yearInterval, RepeatingField(MONTH_OF_YEAR, seasonStartMonth))
      val endInterval = ThisRI(yearInterval, RepeatingField(MONTH_OF_YEAR, seasonEndMonth))
      val seasonInterval = Between(startInterval, endInterval, startIncluded = true, endIncluded = true)
      val timeSteps: Seq[TimeStep] = Seq(TimeStep (seasonInterval.start, seasonInterval.end))
      val attachment = TimEx(seasonTextInterval, timeSteps, seasonText)

      // TextInterval for the TextBoundMention
      val wordInterval = TextInterval(seasonMention.get.firstToken, seasonMention.get.lastToken + 1)
      // create the Mention for this season expression
      new TextBoundMention(
        Seq("Time"),
        wordInterval,
        sentenceIndex,
        doc,
        true,
        getClass.getSimpleName,
        Set(TimeAttachment(attachment))
      )
    }
    mentions
  }
}