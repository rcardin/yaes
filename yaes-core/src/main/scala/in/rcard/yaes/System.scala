package in.rcard.yaes

import in.rcard.yaes.Raise.Raise
import java.lang.System as JSystem

object System {

  type System = Yaes[System.Unsafe]

  def env[A](
      name: String
  )[E](using parser: Parser[E, A])(using env: System, raise: Raise[E]): Option[A] = {
    val maybeEnvValue = unsafe.env(name)
    maybeEnvValue.flatMap { value =>
      parser.parse(value) match {
        case Right(parsedValue) => Some(parsedValue)
        case Left(error)        => Raise.raise(error)
      }
    }
  }

  def env[A](name: String, default: => A)[E](using
      parser: Parser[E, A]
  )(using env: System, raise: Raise[E]): A = {
    System.env(name) match {
      case Some(value) => value
      case None        => default
    }
  }

  def property[A](name: String)[E](using parser: Parser[E, A])(using System, Raise[E]): Option[A] =
    unsafe
      .property(name)
      .flatMap(value =>
        parser.parse(value) match {
          case Right(parsedValue) => Some(parsedValue)
          case Left(error)        => Raise.raise(error)
        }
      )

  def property[A](name: String, default: => A)[E](using
      parser: Parser[E, A]
  )(using System, Raise[E]): A =
    System.property(name) match {
      case Some(value) => value
      case None        => default
    }

  def run[A](block: System ?=> A): A = {
    val handler = new Yaes.Handler[System.Unsafe, A, A] {
      override def handle(program: System ?=> A): A = program(using Yaes(System.unsafe))
    }
    Yaes.handle(block)(using handler)
  }

  private val unsafe: Unsafe = new Unsafe {

    override def property(name: String): Option[String] = Option(JSystem.getProperty(name))

    override def env(name: String): Option[String] = Option(JSystem.getenv(name))
  }

  trait Unsafe {
    def env(name: String): Option[String]
    def property(name: String): Option[String]
  }

  sealed trait Parser[E, A] {
    def parse(value: String): Either[E, A]
  }

  object Parser {
    given Parser[Nothing, String] with {
      def parse(value: String): Either[Nothing, String] = Right(value)
    }

    given Parser[NumberFormatException, Int] with {
      def parse(value: String): Either[NumberFormatException, Int] =
        try {
          Right(value.toInt)
        } catch {
          case e: NumberFormatException => Left(e)
        }
    }

    given Parser[IllegalArgumentException, Boolean] with {
      def parse(value: String): Either[IllegalArgumentException, Boolean] =
        try {
          Right(value.toBoolean)
        } catch {
          case e: IllegalArgumentException => Left(e)
        }
    }
  }
}
