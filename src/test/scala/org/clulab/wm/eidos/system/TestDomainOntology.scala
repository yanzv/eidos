package org.clulab.wm.eidos.system

import ai.lum.common.ConfigUtils._
import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import org.clulab.wm.eidos.EidosSystem
import org.clulab.wm.eidos.groundings._
import org.clulab.wm.eidos.test.EidosTest
import org.clulab.wm.eidoscommon.Canonicalizer
import org.clulab.wm.eidoscommon.utils.Timer
import org.clulab.wm.ontologies.CompactDomainOntology.CompactDomainOntologyBuilder
import org.clulab.wm.ontologies.FastDomainOntology.FastDomainOntologyBuilder
import org.clulab.wm.ontologies.PosNegTreeDomainOntology
import org.clulab.wm.ontologies.PosNegTreeDomainOntology.PosNegTreeDomainOntologyBuilder
import org.clulab.wm.ontologies.{DomainOntology, FullTreeDomainOntology, HalfTreeDomainOntology}

import scala.util.matching.Regex

class TestDomainOntology extends EidosTest {

  def matches(left: DomainOntology, right: DomainOntology): Boolean = {

    def getNames(domainOntology: DomainOntology): Seq[String] =
        domainOntology.nodes.map(_.getName)

    def getValues(domainOntology: DomainOntology): Seq[Array[String]] =
        domainOntology.nodes.map(_.getValues)

    val leftNames = getNames(left)
    val rightNames = getNames(right)

    val leftValues = getValues(left)
    val rightValues = getValues(right)

    leftNames == rightNames && leftValues == rightValues
  }

  def hasDuplicates(name: String, domainOntology: DomainOntology): Boolean = {
    val pathSeq = domainOntology.nodes.map(_.getName)

    domainOntology.nodes.foreach { node =>
      // Just make sure this doesn't crash now.
      val branch = node.getBranchOpt
//      println(branch)
    }

    val pathSet = pathSeq.toSet

//    println(s"""The domain ontology "${domainOntology.name}" node count: ${ontologyNodes.length}""")
//    ontologyNodes.foreach(println)

    if (pathSeq.size != pathSet.size) {
      val pathBag = pathSeq.foldLeft(Map[String, Int]())((map, path) => map + (path -> (map.getOrElse(path, 0) + 1)))
      val duplicatePaths = pathBag.toSeq.filter(_._2 > 1).map(_._1)

      println(s"""The domain ontology "$name" includes duplicate nodes:""")
      duplicatePaths.foreach(println)
      true
    }
    else
      false
  }

  val baseDir = "/org/clulab/wm/eidos/english/ontologies"
  val config = ConfigFactory.load(this.defaultConfig)
      .withValue("ontologies.useGrounding", ConfigValueFactory.fromAnyRef(false, "Don't use vectors when caching ontologies."))
  val reader = new EidosSystem(config)
  val proc = reader.components.procOpt.get
  val canonicalizer = new Canonicalizer(reader.components.stopwordManagerOpt.get, proc.getTagSet)
  val useCacheForOntologies = config[Boolean]("ontologies.useCacheForOntologies")
  val includeParents = config[Boolean]("ontologies.includeParents")
  val filter = true

  def show1(ontology: DomainOntology): Unit = {
    ontology.nodes.foreach { node =>
      println(node.getName + " = " + node.getValues.mkString(", "))
      node.getPatternsOpt.map(_.foreach { pattern: Regex => println(pattern.toString) })
    }
    println
  }

  def show3(newOntology: DomainOntology, newerOntology: DomainOntology, newestOntology: DomainOntology): Unit = {
    show1(newOntology)
    show1(newerOntology)
    show1(newestOntology)
  }

  def testOntology(abbrev: String, name: String, path: String): Unit = {

    def cachePath(name: String): String = OntologyHandler.serializedPath(abbrev, path, includeParents)

    behavior of name

    it should "load and not have duplicates" in {
      val newOntology = Timer.time(s"Load $name without cache") {
        DomainHandler(baseDir + path, "", proc, canonicalizer, filter, useCacheForOntologies = false, includeParents, fmt2 = false)
      }
      hasDuplicates(name, newOntology) should be (false)

      val newerOntologyOpt = Timer.time(s"Convert $name to compact") {
        if (includeParents)
          if (PosNegTreeDomainOntology.isPosNegName(name))
            None
          else
            Some(new FastDomainOntologyBuilder(newOntology.asInstanceOf[FullTreeDomainOntology]).build)
        else
          Some(new CompactDomainOntologyBuilder(newOntology.asInstanceOf[HalfTreeDomainOntology]).build)
      }
      newerOntologyOpt.foreach { newerOntology =>
        hasDuplicates(name, newerOntology) should be(false)
        matches(newOntology, newerOntology)

        if (useCacheForOntologies) {
          val newestOntology = Timer.time(s"Load $name from cache") {
            DomainHandler("", cachePath(abbrev), proc, canonicalizer, filter, useCacheForOntologies = true, includeParents)
          }

          show3(newOntology, newerOntology, newestOntology)
          hasDuplicates(name, newestOntology) should be(false)
          matches(newOntology, newestOntology)
        }
      }
    }
  }

  testOntology("un", "un ontology", "/un_ontology.yml")
  testOntology("mitre12", "mitre12 ontology", "/mitre12_indicators.yml")
//  testOntology("topo", "topoFlow ontology", "/topoflow_ontology.yml")
//  testOntology("mesh", "mesh ontology", "/mesh_ontology.yml")
  testOntology("props", "props ontology", "/un_properties.yml")
  // testOntology("wm", "wm ontology", "/wm_metadata.yml") // deprecated
  // testOntology("wm_flattened", "wm flattened ontology", "/wm_with_flattened_interventions_metadata.yml") // deprecated
}
