package org.clulab.wm.ontologies

import org.clulab.wm.eidoscommon.utils.Closer.AutoCloser
import org.clulab.wm.eidoscommon.utils.FileUtils
import org.clulab.wm.eidoscommon.utils.TsvReader

import java.time.ZonedDateTime
import java.util

import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.{HashMap => MutableHashMap}
import scala.util.matching.Regex

/**
  * Provide a DomainOntology interface on top of the Arrays of String and Int values.
  *
  * @param leafStrings All the strings used in the leaves of the ontology
  * @param leafStringIndexes Indexes into leafStrings sorted by leaf node
  * @param leafStartIndexes Where to start in leafStringIndexes to find the indexes for leaf node N
  * @param patternStrings All the regex strings used in the leaves of the ontology
  * @param patternStartIndexes Where to start in patternStrings to find the patterns for leaf node N
  * @param nodeStrings All the strings used in the non-leaf nodes of the ontology
  * @param leafIndexes Parent offset, name offset, parent offset, name offset, ...  for leaves only
  *                    Name offset is into nodeStrings, parent offset is into branchIndexes.
  * @param branchIndexes Parent offset, name offset, parent offset, name offset, ... for non-leaves only
  *                      Name offset is into nodeStrings, parent offset is back into branchIndexes.
  */
class CompactDomainOntology(
  protected val leafStrings: Array[String],
  protected val leafStringIndexes: Array[Int],
  protected val leafStartIndexes: Array[Int],

  patternStrings: Array[String],
  protected val patternStartIndexes: Array[Int],

  protected val nodeStrings: Array[String],

  protected val leafIndexes: Array[Int],
  protected val branchIndexes: Array[Int],

  override val version: Option[String] = None,
  override val date: Option[ZonedDateTime]
) extends DomainOntology with IndexedDomainOntology with IndexedSeq[IndexedDomainOntologyNode] {
  protected val patternRegexes: Array[Regex] = patternStrings.map(_.r)

  def getValues(n: Integer): Array[String] = {
    Range(leafStartIndexes(n), leafStartIndexes(n + 1))
        .map(n => leafStrings(leafStringIndexes(n)))
        .toArray
  }

  // TODO: This will not always store just the leaves.
  def isLeaf(n: Integer): Boolean = false

  def getPatterns(n: Integer): Option[Array[Regex]] = {
    val range = Range(patternStartIndexes(n), patternStartIndexes(n + 1))

    if (range.isEmpty) None
    else Some(range.map(n => patternRegexes(n)).toArray)
  }

  def save(filename: String): Unit = {
    FileUtils.newObjectOutputStream(filename).autoClose { objectOutputStream =>
      val firstLine = Seq(
        version.getOrElse(""),
        date.map(_.toString).getOrElse("")
      ).mkString("\t") // Some versions of ZonedDateTime.toString can contain spaces.
      objectOutputStream.writeObject(firstLine)
      objectOutputStream.writeObject(leafStrings.mkString("\n"))
      objectOutputStream.writeObject(leafStringIndexes)
      objectOutputStream.writeObject(leafStartIndexes)
      objectOutputStream.writeObject(patternStrings.mkString("\n"))
      objectOutputStream.writeObject(patternStartIndexes)
      objectOutputStream.writeObject(nodeStrings.mkString("\n"))
      objectOutputStream.writeObject(leafIndexes)
      objectOutputStream.writeObject(branchIndexes)
    }
  }

  override def nodes: IndexedSeq[IndexedDomainOntologyNode] = this

  override def length: Int = leafIndexes.length / CompactDomainOntology.leafIndexWidth

  override def apply(idx: Int): IndexedDomainOntologyNode = new IndexedDomainOntologyNode(this, idx)

  override def getParent(n: Integer): Option[Option[DomainOntologyNode]] = None // unknown

  override def getName(n: Integer): String = {
    val stringBuilder = new StringBuilder()

    def parentName(n: Int): Unit = {
      if (n > 0) {
        val index = n * CompactDomainOntology.branchIndexWidth
        val parentOffset = branchIndexes(index + CompactDomainOntology.parentOffset)
        val nameOffset = branchIndexes(index + CompactDomainOntology.nameOffset)

        parentName(parentOffset)
        stringBuilder.append(nodeStrings(nameOffset))
        stringBuilder.append(DomainOntology.SEPARATOR)
      }
    }

    val index = n * CompactDomainOntology.leafIndexWidth
    val parentOffset = leafIndexes(index + CompactDomainOntology.parentOffset)
    val nameOffset = leafIndexes(index + CompactDomainOntology.nameOffset)

    parentName(parentOffset)
    stringBuilder.append(nodeStrings(nameOffset))
    stringBuilder.result()
  }

  override def getSimpleName(n: Integer): String = {
    val index = n * CompactDomainOntology.leafIndexWidth
    val nameOffset = leafIndexes(index + CompactDomainOntology.nameOffset)

    nodeStrings(nameOffset)
  }

  override def getBranch(n: Integer): Option[String] = {

    def branch(n: Int, prevNameOffset: Int): Option[String] = {
      if (n > 0) {
        val index = n * CompactDomainOntology.branchIndexWidth
        val parentOffset = branchIndexes(index + CompactDomainOntology.parentOffset)

        if (parentOffset == 0)
          if (prevNameOffset >= 0) Some(nodeStrings(prevNameOffset))
          else None
        else {
          val nameOffset = branchIndexes(index + CompactDomainOntology.nameOffset)

          branch(parentOffset, nameOffset)
        }
      }
      else None
    }

    // This will always be run on an n that corresponds to a leaf.
    val index = n * CompactDomainOntology.leafIndexWidth
    val parentOffset = leafIndexes(index + CompactDomainOntology.parentOffset)

    branch(parentOffset, -1)
  }
}

object CompactDomainOntology {
  val branchIndexWidth = 2
  val leafIndexWidth = 2

  val parentOffset = 0
  val nameOffset = 1

  def load(filename: String): CompactDomainOntology = {

    def splitText(text: String): Array[String] = text.split('\n')

    FileUtils.newClassLoaderObjectInputStream(filename, this).autoClose { objectInputStream =>
      val (versionOpt: Option[String], dateOpt: Option[ZonedDateTime]) = {
        val firstLine = objectInputStream.readObject().asInstanceOf[String]
        val tsvReader = new TsvReader()
        val Array(commit, date) = tsvReader.readln(firstLine)
        val commitOpt = if (commit.nonEmpty) Some(commit) else None
        val dateOpt = if (date.nonEmpty) Some(ZonedDateTime.parse(date)) else None

        (commitOpt, dateOpt)
      }
      val leafStrings = splitText(objectInputStream.readObject().asInstanceOf[String])
      val leafStringIndexes = objectInputStream.readObject().asInstanceOf[Array[Int]]
      val leafStartIndexes = objectInputStream.readObject().asInstanceOf[Array[Int]]
      val patternStrings = splitText(objectInputStream.readObject().asInstanceOf[String])
      val patternStartIndexes = objectInputStream.readObject().asInstanceOf[Array[Int]]
      val nodeStrings = splitText(objectInputStream.readObject().asInstanceOf[String])
      val leafIndexes = objectInputStream.readObject().asInstanceOf[Array[Int]]
      val branchIndexes = objectInputStream.readObject().asInstanceOf[Array[Int]]

      new CompactDomainOntology(leafStrings, leafStringIndexes, leafStartIndexes, patternStrings, patternStartIndexes,
          nodeStrings, leafIndexes, branchIndexes, versionOpt, dateOpt)
    }
  }

  class CompactDomainOntologyBuilder(treeDomainOntology: HalfTreeDomainOntology) {

    protected def append(strings: MutableHashMap[String, Int], string: String): Unit =
       if (!strings.contains(string))
          strings.put(string, strings.size)

    protected def mkParentMap(): util.IdentityHashMap[HalfOntologyParentNode, (Int, Int)] = {
      // This is myIndex, parentIndex
      val parentMap: util.IdentityHashMap[HalfOntologyParentNode, (Int, Int)] = new util.IdentityHashMap()

      def append(parents: Seq[HalfOntologyParentNode]): Int =
          if (parents.nonEmpty)
            if (parentMap.containsKey(parents.head))
              parentMap.get(parents.head)._1
            else {
              val parentIndex = append(parents.tail) // Put root on top.
              val myIndex = parentMap.size
              parentMap.put(parents.head, (myIndex, parentIndex))
              myIndex
            }
          else
            -1 // This is important!

      treeDomainOntology.nodes.foreach { node => append(node.parents) }
      parentMap
    }

    protected def mkLeafStringMap(): MutableHashMap[String, Int] = {
      val stringMap: MutableHashMap[String, Int] = new MutableHashMap()

      treeDomainOntology.nodes.foreach { node =>
        node.getValues.foreach(append(stringMap, _))
      }
      stringMap
    }

    protected def mkPatternStringAndStartIndexes(): (Array[String], Array[Int]) = {
      val stringBuffer = new ArrayBuffer[String]()
      val nodes = treeDomainOntology.nodes
      val size = nodes.size
      val startIndexBuffer = new Array[Int](size + 1)

      nodes.zipWithIndex.foreach { case (node, i) =>
        startIndexBuffer(i) = stringBuffer.size

        val optionRegexes = node.getPatterns
        if (optionRegexes.isDefined)
              stringBuffer.appendAll(optionRegexes.get.map(_.toString))
      }
      startIndexBuffer(size) = stringBuffer.size // extra
      (stringBuffer.toArray, startIndexBuffer)
    }

    protected def mkNodeStringMap(parentMap: util.IdentityHashMap[HalfOntologyParentNode, (Int, Int)]): MutableHashMap[String, Int] = {
      // TODO: Fix this code.  Try to sort entrySet.      
      val stringMap: MutableHashMap[String, Int] = new MutableHashMap()
      val parentSeq = parentMap
          .entrySet
          .asScala
          .toSeq
          .map { entrySet => (entrySet.getKey, entrySet.getValue) }
          .sortBy(_._2)

      parentSeq.foreach { case (ontologyParentNode, _)  =>
        append(stringMap, DomainOntology.escaped(ontologyParentNode.getSimpleName))
      }
      treeDomainOntology.nodes.foreach { node =>
        append(stringMap, DomainOntology.escaped(node.getSimpleName))
      }
      stringMap
    }

    protected def mkLeafStringAndStartIndexes(leafStringMap: MutableHashMap[String, Int]): (Array[Int], Array[Int]) = {
      val stringIndexBuffer = new ArrayBuffer[Int]()
      val startIndexBuffer = new ArrayBuffer[Int]()

      treeDomainOntology.nodes.foreach { node =>
        startIndexBuffer += stringIndexBuffer.size
        node.getValues.foreach { value =>
          stringIndexBuffer += leafStringMap(value)
        }
      }
      startIndexBuffer += stringIndexBuffer.size // extra
      (stringIndexBuffer.toArray, startIndexBuffer.toArray)
    }

    protected def mkLeafIndexes(parentMap: util.IdentityHashMap[HalfOntologyParentNode, (Int, Int)], stringMap: MutableHashMap[String, Int]): Array[Int] = {
      val indexBuffer = new ArrayBuffer[Int]()

      treeDomainOntology.nodes.foreach { node =>
        indexBuffer += parentMap.get(node.getParent.get.get)._1 // parentOffset
        indexBuffer += stringMap(DomainOntology.escaped(node.getSimpleName)) // nameOffset
      }
      indexBuffer.toArray
    }

    protected def mkParentIndexes(parentMap: util.IdentityHashMap[HalfOntologyParentNode, (Int, Int)], stringMap: MutableHashMap[String, Int]): Array[Int] = {
      val indexBuffer = new ArrayBuffer[Int]()
      val keysAndValues: Array[(HalfOntologyParentNode, (Int, Int))] = parentMap.asScala.toArray.sortBy(_._2._1)

      keysAndValues.foreach { case (branchNode, (_, parentIndex)) =>
        indexBuffer += parentIndex // parentOffset
        indexBuffer += stringMap(DomainOntology.escaped(branchNode.getSimpleName)) // nameOffset
      }
      indexBuffer.toArray
    }

    def build(): DomainOntology = {
      val parentMap: util.IdentityHashMap[HalfOntologyParentNode, (Int, Int)] = mkParentMap()
      val leafStringMap: MutableHashMap[String, Int] = mkLeafStringMap()
      val nodeStringMap: MutableHashMap[String, Int] = mkNodeStringMap(parentMap)
      val (leafStringIndexes, leafStartIndexes) = mkLeafStringAndStartIndexes(leafStringMap)
      val (patternStrings, patternStartIndexes) = mkPatternStringAndStartIndexes()
      val leafIndexes = mkLeafIndexes(parentMap, nodeStringMap)
      val branchIndexes = mkParentIndexes(parentMap, nodeStringMap)

      // This sorts by the latter, the Int, and then answers the former, the String.
      def toArray(stringMap:MutableHashMap[String, Int]): Array[String] =
          stringMap.toArray.sortBy(_._2).map(_._1)

      val leafStrings: Array[String] = toArray(leafStringMap)
      val nodeStrings: Array[String] = toArray(nodeStringMap)

      new CompactDomainOntology(leafStrings, leafStringIndexes, leafStartIndexes, patternStrings, patternStartIndexes,
          nodeStrings, leafIndexes, branchIndexes, treeDomainOntology.version, treeDomainOntology.date)
    }
  }
}
