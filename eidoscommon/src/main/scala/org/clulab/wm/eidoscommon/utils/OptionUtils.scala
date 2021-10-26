package org.clulab.wm.eidoscommon.utils

object OptionUtils {

  protected type IsEmptyAble = { def isEmpty: Boolean}

  def someOrNoneIfEmpty[C <: IsEmptyAble](collection: C): Option[C] = {
    if (collection.isEmpty) None
    else Some(collection)
  }
}
