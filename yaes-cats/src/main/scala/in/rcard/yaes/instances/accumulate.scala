package in.rcard.yaes.instances

import cats.data.{NonEmptyChain, NonEmptyList}
import in.rcard.yaes.Raise.AccumulateCollector

/** Provides AccumulateCollector instances for Cats data types. */
object accumulate extends AccumulateInstances

/** Provides AccumulateCollector instances for Cats data types.
  *
  * This trait provides collector instances that enable polymorphic error accumulation
  * with Cats' NonEmptyList and NonEmptyChain types.
  */
trait AccumulateInstances {

  /** Collector instance for NonEmptyList - collects errors into a NonEmptyList.
    *
    * Import this instance to enable polymorphic accumulation with NonEmptyList:
    *
    * Example:
    * {{{
    * import in.rcard.yaes.Raise
    * import in.rcard.yaes.Raise.accumulating
    * import in.rcard.yaes.instances.accumulate.given
    * import cats.data.NonEmptyList
    *
    * val result: Either[NonEmptyList[String], Int] = Raise.either {
    *   Raise.accumulate[NonEmptyList, String, Int] {
    *     val a = accumulating { Raise.raise("error1") }
    *     val b = accumulating { Raise.raise("error2") }
    *     a + b
    *   }
    * }
    * // result will be Left(NonEmptyList("error1", List("error2")))
    * }}}
    */
  given nelCollector: AccumulateCollector[NonEmptyList] with {
    def collect[Error](head: Error, tail: List[Error]): NonEmptyList[Error] =
      NonEmptyList(head, tail)
  }

  /** Collector instance for NonEmptyChain - collects errors into a NonEmptyChain.
    *
    * Import this instance to enable polymorphic accumulation with NonEmptyChain:
    *
    * Example:
    * {{{
    * import in.rcard.yaes.Raise
    * import in.rcard.yaes.Raise.accumulating
    * import in.rcard.yaes.instances.accumulate.given
    * import cats.data.NonEmptyChain
    *
    * val result: Either[NonEmptyChain[String], Int] = Raise.either {
    *   Raise.accumulate[NonEmptyChain, String, Int] {
    *     val a = accumulating { Raise.raise("error1") }
    *     val b = accumulating { Raise.raise("error2") }
    *     a + b
    *   }
    * }
    * // result will be Left(NonEmptyChain("error1", "error2"))
    * }}}
    */
  given necCollector: AccumulateCollector[NonEmptyChain] with {
    def collect[Error](head: Error, tail: List[Error]): NonEmptyChain[Error] =
      NonEmptyChain(head, tail*)
  }
}
