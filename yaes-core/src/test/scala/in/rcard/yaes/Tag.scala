package in.rcard.yaes

import scala.quoted.{Type, Quotes, Expr}

trait Tag[T]:
  def tag: String

object Tag:
  inline given [T]: Tag[T] = ${ impl[T] }

  /* Using `show` is hacky and broken in non-trivial cases, you should
   * get a TypeRepr from the Type and implement it properly.
   */
  def impl[T: Type](using Quotes): Expr[Tag[T]] =
    val out = Expr(Type.show[T])
    '{
      new Tag[T]:
        def tag = ${ out }
    }
