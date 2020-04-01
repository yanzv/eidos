package org.clulab.wm.eidos.system

import org.clulab.processors.clu.tokenizer.RawToken
import org.clulab.wm.eidos.EidosEnglishProcessor
import org.clulab.wm.eidos.test.TestUtils._

class TestEidosTokenizer extends EnglishTest {

  behavior of "normalization"

  val eidosTokenizer = ieSystem.components.proc.asInstanceOf[EidosEnglishProcessor].tokenizer

  it should "not change plain text" in {
    val oldText = "This is a test."
    val newText = eidosTokenizer.normalize(oldText)._1

    newText should be (oldText)
  }

  it should "normalize abnormal text" in {
    val inputsAndExpecteds = Seq(
      ("e\ufb00ort", "effort"),
      ("a\ufb01eld", "afield"),
      ("e\ufb03cient", "efficient"), // (ffi)
      ("e\ufb00icient", "efficient"), // (ff)i
      ("ef\ufb01cient", "efficient"), // f(fi)
    )

    inputsAndExpecteds.foreach { case (input, expected) =>
      val actual = eidosTokenizer.normalize(input)._1

      actual should be (expected)
    }
  }

  it should "make unicode substitutions" in {
    val inputsAndExpecteds = Seq(
      // (input, keepAccents = false, keepAccents = true)
      ("(alpha)",  "(alpha)", "(alpha)"),
      ("(\u0277)", "(omega)", "(omega)"),
      ("(\u0103)", "(a)",      "(a)"),      // a-umlaut, in unicode list, not in accent list
      ("(\u00e1)", "(a)",      "(\u00e1)"), // a-acute, in unicode list, in accent list
      ("(\u017f)", "( )",      "( )"),      // long s, not in unicode list, not in accent list
      ("(\u00b8)", "()",       "()")        // unknown, in unicode list empty, not in accent list
    )

    inputsAndExpecteds.foreach { case (input, expectedFalse, expectedTrue) =>
      val actualFalse = eidosTokenizer.sanitize(input, false)._1
      val actualTrue =  eidosTokenizer.sanitize(input, true)._1

      actualFalse should be (expectedFalse)
      actualTrue should be (expectedTrue)
    }
  }

  it should "tokenize unicode corrrectly" in {
    val text = "(\u0277)"
    val tokens = eidosTokenizer.entoken(text)

    tokens should have size (4)
    tokens(0) should be (RawToken("(", 0, 1, "("))
    tokens(1) should be (RawToken("\u0277", 1, 2, "omega"))
    tokens(2) should be (RawToken(")", 2, 3, ")"))
    tokens(3) should be (RawToken("", 3, 3, ".")) // The paragraph splitter ands a trailing period.
  }
}
