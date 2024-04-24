package library.testing

import io.kotest.assertions.failure
import io.kotest.assertions.shouldFail
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class WithCluesTest : FunSpec({
    test("one clue") {
        shouldFail {
            withClues({ "a" }) {
                throw failure("failure")
            }
        }.message.let {
            it shouldBe "### Clues:\na\nfailure"
        }
    }

    test("two clues") {
        shouldFail {
            withClues({ "a" }, { "b" }) {
                throw failure("failure")
            }
        }.message.let {
            it shouldBe "### Clues:\na\nb\nfailure"
        }
    }

    test("no clues") {
        shouldFail {
            withClues {
                throw failure("failure")
            }
        }.message.let {
            it shouldBe "failure"
        }
    }

    test("clues on exception without invoking `failure`") {
        shouldFail {
            withClues({ "a" }) {
                throw AssertionError("assertion")
            }
        }.message.let {
            it shouldBe "### Clues:\na\nassertion"
        }
    }
})
