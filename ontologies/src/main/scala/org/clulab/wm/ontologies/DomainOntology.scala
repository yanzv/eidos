package org.clulab.wm.ontologies

import java.time.ZonedDateTime
import org.clulab.struct.Interval
import org.clulab.wm.eidoscommon.Canonicalizer
import org.clulab.wm.eidoscommon.SentencesExtractor
import org.clulab.wm.eidoscommon.utils.Namer

import scala.util.matching.Regex

trait DomainOntologyNode extends Namer {

  def getValues: Array[String]

  def getPosValues: Array[String] = getValues

  def getNegValues: Array[String] = Array.empty

  def getPatternsOpt: Option[Array[Regex]]

  def getPosExamplesOpt: Option[Array[String]] = getExamplesOpt

  def getNegExamplesOpt: Option[Array[String]] = None

  def getExamplesOpt: Option[Array[String]] = None // Some subclasses don't keep track of this.

  def isLeaf: Boolean

  // If the Node is oblivious about its parents, for example if the information is not stored, then None.
  // If the Node knows of its parents, but there isn't one, because it's the root, then Some(None).
  // If The Node knows of its parents and it is not the root, then Some(Some(DomainOntologyNode)).
  def getParentOptOpt: Option[Option[DomainOntologyNode]]
  // This is the long name that looks like a path, but with no leading /.
  def getName: String

  def getSimpleName: String = Namer.getSimpleName(getName)

  def getBranchOpt: Option[String] = Namer.getBranch(getName)
}

trait DomainOntology {
    def versionOpt: Option[String] = None
    def dateOpt: Option[ZonedDateTime] = None

    def nodes: IndexedSeq[DomainOntologyNode]
    def save(filename: String): Unit
}

object DomainOntology {
  val ESCAPE = "\\"
  val ESCAPED_ESCAPE: String = ESCAPE + ESCAPE
  val SEPARATOR = "/"
  val ESCAPED_SEPARATOR: String = ESCAPE + SEPARATOR

  // This takes care of possible /s in the node names so that paths can be built.
  def escaped(simpleName: String): String = simpleName
      .replace(ESCAPE, ESCAPED_ESCAPE)
      .replace(SEPARATOR, ESCAPED_SEPARATOR)

  // Sometimes the words in node names are concatenated with _.
  def unescaped(simpleName: String): String = simpleName
      .replace('_', ' ')

  // We're doing case insensitive matching.
  def toRegex(pattern: String): Regex = ("(?i)" + pattern).r

  def canonicalWordsFromSentence(sentencesExtractor: SentencesExtractor, canonicalizer: Canonicalizer,  text: String): Seq[String] = {
    for {
      s <- sentencesExtractor.extractSentences(text)
      canonicalWord <- canonicalizer.canonicalWordsFromSentence(s, Interval(0, s.words.length))
    } yield canonicalWord
  }
}

abstract class VersionedDomainOntology(
    override val versionOpt: Option[String],
    override val dateOpt: Option[ZonedDateTime]
) extends DomainOntology

trait IndexedDomainOntology {
  def getValues(n: Integer): Array[String]
  def getPosValues(n: Integer): Array[String] = getValues(n)
  def getNegValues(n: Integer): Array[String] = Array.empty
  def getPatternsOpt(n: Integer): Option[Array[Regex]]
  def isLeaf(n: Integer): Boolean
  def getParentOptOpt(n: Integer): Option[Option[DomainOntologyNode]]
  def getName(n: Integer): String
  def getSimpleName(n: Integer): String
  def getBranchOpt(n: Integer): Option[String]
  def getExamplesOpt(n: Integer): Option[Array[String]] = None // Some subclasses don't keep track of this.
}

class IndexedDomainOntologyNode(indexedDomainOntology: IndexedDomainOntology, index: Int) extends DomainOntologyNode {

  override def getValues: Array[String] = indexedDomainOntology.getValues(index)

  override def getPatternsOpt: Option[Array[Regex]] = indexedDomainOntology.getPatternsOpt(index)

  override def isLeaf: Boolean = indexedDomainOntology.isLeaf(index)

  override def getParentOptOpt: Option[Option[DomainOntologyNode]] = indexedDomainOntology.getParentOptOpt(index)

  override def getName: String = indexedDomainOntology.getName(index)

  override def getSimpleName: String = indexedDomainOntology.getSimpleName(index)

  override def getBranchOpt: Option[String] = indexedDomainOntology.getBranchOpt(index)

  override def getExamplesOpt: Option[Array[String]] = indexedDomainOntology.getExamplesOpt(index)
}
