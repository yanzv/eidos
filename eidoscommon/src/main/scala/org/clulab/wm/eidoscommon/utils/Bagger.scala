package org.clulab.wm.eidoscommon.utils

import scala.collection.mutable

trait Bagger[T] {
  def noBlock(value: T): Unit = ()
  def put(value: T): Bagger[T]
  def put(values: Seq[T]): Bagger[T]
  def putIfNew(value: T)(block: T => Unit): Bagger[T]
  def get: Seq[T]
  def get(value: T): Int
  def getEntries: Seq[(T, Int)]
  def keySize: Int
  def valueSize: Int
}

object Bagger {

  def addAll[T](bagger: Bagger[T], values: Seq[T], getNeighbors: T => Iterable[T]): Bagger[T] = {

    def addAll(value: T): Unit = {
      bagger.putIfNew(value) { value =>
        getNeighbors(value).foreach(addAll)
      }
    }

    values.foreach(addAll)
    bagger
  }
}

class EqualityBagger[T] extends Bagger[T] {
  protected val map = new mutable.HashMap[T, Int]()
  var valueCount: Int = 0

  def put(value: T): EqualityBagger[T] = {
    putIfNew(value)(noBlock)
    this
  }

  def put(values: Seq[T]): EqualityBagger[T] = {
    values.foreach { value =>
      putIfNew(value)(noBlock)
    }
    this
  }

  protected def put(key: T, value: Int): EqualityBagger[T] = {
    map(key) = value
    valueCount += 1
    this
  }

  def putIfNew(value: T)(block: T => Unit): EqualityBagger[T] = {
    val count = map.getOrElse(value, 0)

    map(value) = count + 1
    if (count == 0)
      block(value)
    this
  }

  def get: Seq[T] = map.keySet.toSeq

  def get(value: T): Int = map.getOrElse(value, 0)

  def getEntries: Seq[(T, Int)] = map.toSeq

  def keySize: Int = map.size

  def valueSize: Int = valueCount
}

object EqualityBagger {

  def apply[T](): EqualityBagger[T] = new EqualityBagger[T]()

  def apply[T](values: Seq[T]): EqualityBagger[T] = apply[T]().put(values)

  def apply[T](values: Seq[T], getNeighbors: T => Iterable[T]): EqualityBagger[T] = {
    val bagger = apply[T]()

    Bagger.addAll(bagger, values, getNeighbors)
    bagger
  }
}

class IdentityBagger[T <: AnyRef] extends Bagger[T] {
  protected val map = new IdentityHashMap[T, Int]()
  var valueCount: Int = 0

  def put(value: T): IdentityBagger[T] = {
    putIfNew(value)(noBlock)
    this
  }

  def put(values: Seq[T]): IdentityBagger[T] = {
    values.foreach { value =>
      putIfNew(value)(noBlock)
    }
    this
  }

  protected def put(key: T, value: Int): IdentityBagger[T] = {
    map(key) = value
    valueCount += 1
    this
  }

  def putIfNew(value: T)(block: T => Unit): IdentityBagger[T] = {
    if (map.contains(value))
      map(value) = map(value) + 1
    else {
      map(value) = 1
      block(value)
    }
    this
  }

  def get: Seq[T] = map.keys.toSeq

  def get(value: T): Int = map.getOrElse(value, 0)

  def getEntries: Seq[(T, Int)] = map.toSeq

  def keySize: Int = map.size

  def valueSize: Int = valueCount
}

object IdentityBagger {

  def apply[T <: AnyRef](): IdentityBagger[T] = new IdentityBagger[T]()

  def apply[T <: AnyRef](values: Seq[T]): IdentityBagger[T] = apply[T]().put(values)

  def apply[T <: AnyRef](values: Seq[T], getNeighbors: T => Iterable[T]): IdentityBagger[T] = {
    val bagger = apply[T]()

    Bagger.addAll(bagger, values, getNeighbors)
    bagger
  }
}
