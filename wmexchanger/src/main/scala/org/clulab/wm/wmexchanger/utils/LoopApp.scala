package org.clulab.wm.wmexchanger.utils

import org.clulab.wm.eidoscommon.utils.Logging

trait LoopApp extends Logging {

  // Returns whether to continue looping or not.
  def safeLoop(f: () => SafeThread): Boolean = {
    try {
      val thread = f()
      thread.join()
      !(thread.userInterruption || thread.isInterrupted)
    }
    catch {
      case throwable: Throwable =>
        logger.error("", throwable)
        true
    }
  }

  def loop(f: () => SafeThread): Unit = {
    // Keep looping until true is returned, indicating normal loop termination.
    while (safeLoop(f)) Thread.sleep(5000)
  }

  def getArgOrEnv(args: Array[String], index: Int, name: String): String = {
    // Only use the environment variables if there aren't any command line arguments at all.
    if (args.isEmpty) {
      val envOpt = Option(System.getenv(name))

      envOpt.getOrElse(throw new RuntimeException("There is no arg($index) or environment variable '$name'."))
    }
    else {
      val argOpt = args.lift(index)

      argOpt.getOrElse(throw new RuntimeException("There are args but no arg($index)."))
    }
  }
}
