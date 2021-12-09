package org.clulab.wm.eidoscommon.utils

trait Namer {
  def getName: String // gets the entire name with parts separated by /
  def getBranchOpt: Option[String] // gets the branch in top/branch/[more/]leaf
  def getSimpleName: String // gets the leaf name

  def canonicalName: String = Namer.canonicalize(getSimpleName) // replaces the _s with spaces
  def canonicalWords: Array[String] = Namer.canonicalizeWords(getSimpleName) // splits the canonical name

  override def toString: String = getName
}

object Namer {
  val slash = '/'
  val underscore = '_'

  implicit val NameOrdering: Ordering[Namer] = new Ordering[Namer] {
    override def compare(x: Namer, y: Namer): Int = x.getName.compare(y.getName)
  }

  def getBranch(name: String): Option[String] = {
    val count = name.count(char => char == slash)

    if (count >= 2)
      Some(StringUtils.beforeFirst(StringUtils.afterFirst(name, slash, false), slash, false))
    else
      None
  }

  def getSimpleName(name: String): String = StringUtils.afterLast(name, slash, all = true)

  def canonicalize(simpleName: String): String = {
    val lowerName = simpleName.toLowerCase
    val separatedName = lowerName.replace(underscore, ' ')

    separatedName
  }

  def canonicalizeWords(simpleName: String): Array[String] = {
    if (simpleName.nonEmpty) {
      val lowerName = simpleName.toLowerCase
      val separatedNames = lowerName.split(underscore)

      separatedNames
    }
    else
      Array.empty
  }
}

// This is mostly for deserialization.  When we read back a serialization,
// it may not be possible to match it back up to something with nodes.
class PassThruNamer(val name: String) extends Namer {

  def getBranchOpt: Option[String] = Namer.getBranch(name)

  def getName: String = name

  def getSimpleName: String = Namer.getSimpleName(name)
}
