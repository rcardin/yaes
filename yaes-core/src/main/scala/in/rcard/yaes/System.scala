package in.rcard.yaes

import java.lang.System as JSystem

type System = Yaes[System.Unsafe]

/** Companion object providing convenient methods for working with the `System` effect.
  *
  * The `System` effect provides access to system properties and environment variables. It allows
  * you to read values from the system environment and properties in a type-safe manner.
  *
  * The following types are supported for parsing:
  *   - `String`
  *   - `Int`
  *   - `Long`
  *   - `Double`
  *   - `Float`
  *   - `Short`
  *   - `Byte`
  *   - `Boolean`
  *   - `Char`
  *
  * Example Usage:
  * {{{
  * Raise.run {
  *   System.run {
  *     val path: Option[String] = System.env("PATH")
  *     val javaHome: String = System.env("JAVA_HOME", "/usr/lib/jvm")
  *
  *     val port: Option[Int] = System.property[Int]("server.port")
  *     val timeout: Int = System.property[Int]("server.timeout", 30)
  *   }
  * }
  * }}}
  */
object System {

  /** Lifts a block of code into the System effect.
    *
    * @param block
    *   The code block to be lifted into the System effect
    * @param env
    *   The System effect provided through context parameters
    * @return
    *   The block with the System effect
    */
  def apply[A](block: => A)(using env: System): A = block

  /** Retrieves an environment variable of type `A` by name, raising an error of type `E` if the
    * string representation of the variable cannot be parsed into the desired type.
    *
    * Example:
    * {{{
    * val maybePort: (System, Raise[NumberFormatException]) ?=> Option[Int] = System.env[Int]("PORT")
    * }}}
    *
    * @param name
    *   The name of the environment variable
    * @param parser
    *   The parser to convert the string value to the desired type
    * @param env
    *   The `System` effect provided through context parameters
    * @param raise
    *   The `Raise` effect for error handling
    * @return
    *   An [[Option]] containing the parsed value, or [[None]] if the variable is not set
    * @tparam A
    *   The type to which the environment variable should be parsed
    * @tparam E
    *   The type of error that can occur during parsing
    * @see
    *   [[Raise]]
    */
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

  /** Retrieves an environment variable of type `A` by name, returning a default value if the
    * variable is not set. It raises an error of type `E` if the string representation of the
    * variable cannot be parsed into the desired type.
    *
    * Example:
    * {{{
    * val maybePort: (System, Raise[NumberFormatException]) ?=> Int = System.env[Int]("PORT", 8080)
    * }}}
    *
    * @param name
    *   The name of the environment variable
    * @param default
    *   The default value to return if the variable is not set
    * @param parser
    *   The parser to convert the string value to the desired type
    * @param env
    *   The `System` effect provided through context parameters
    * @return
    *   The parsed value or the default value if the variable is not set
    * @tparam A
    *   The type to which the environment variable should be parsed
    * @tparam E
    *   The type of error that can occur during parsing
    * @see
    *   [[Raise]]
    */
  def env[A](name: String, default: => A)[E](using
      parser: Parser[E, A]
  )(using env: System, raise: Raise[E]): A = {
    System.env(name) match {
      case Some(value) => value
      case None        => default
    }
  }

  /** Retrieves a system property of type `A` by name, raising an error of type `E` if the string
    * representation of the property cannot be parsed into the desired type.
    *
    * Example:
    * {{{
    * val maybePort: (System, Raise[NumberFormatException]) ?=> Option[Int] = System.property[Int]("server.port")
    * }}}
    *
    * @param name
    *   The name of the system property
    * @param parser
    *   The parser to convert the string value to the desired type
    * @param env
    *   The `System` effect provided through context parameters
    * @param raise
    *   The `Raise` effect for error handling
    * @return
    *   An [[Option]] containing the parsed value, or [[None]] if the property is not set
    * @tparam A
    *   The type to which the system property should be parsed
    * @tparam E
    *   The type of error that can occur during parsing
    * @see
    *   [[Raise]]
    */
  def property[A](name: String)[E](using parser: Parser[E, A])(using System, Raise[E]): Option[A] =
    unsafe
      .property(name)
      .flatMap(value =>
        parser.parse(value) match {
          case Right(parsedValue) => Some(parsedValue)
          case Left(error)        => Raise.raise(error)
        }
      )

  /** Retrieves a system property of type `A` by name, returning a default value if the property is
    * not set. It raises an error of type `E` if the string representation of the property cannot be
    * parsed into the desired type.
    *
    * Example:
    * {{{
    * val maybePort: (System, Raise[NumberFormatException]) ?=> Int = System.property[Int]("server.port", 8080)
    * }}}
    *
    * @param name
    *   The name of the system property
    * @param default
    *   The default value to return if the property is not set
    * @param parser
    *   The parser to convert the string value to the desired type
    * @param env
    *   The `System` effect provided through context parameters
    * @return
    *   The parsed value or the default value if the property is not set
    * @tparam A
    *   The type to which the system property should be parsed
    * @tparam E
    *   The type of error that can occur during parsing
    * @see
    *   [[Raise]]
    */
  def property[A](name: String, default: => A)[E](using
      parser: Parser[E, A]
  )(using System, Raise[E]): A =
    System.property(name) match {
      case Some(value) => value
      case None        => default
    }

  /** Runs a program that requires the `System` effect.
    *
    * This method handles the `System` effect by supplying the implementation that uses the
    * `java.lang.System` class to access system properties and environment variables.
    *
    * Example:
    * {{{
    * val path: String = System.run { System.env[String]("PATH") }
    * }}}
    *
    * @param block
    *   The code block to be run with the `System` effect
    * @return
    *   The result of the code block
    */
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

  /** Unsafe interface for accessing system properties and environment variables.
    *
    * This trait provides methods to access system properties and environment variables without any
    * safety checks. It is intended for internal use only.
    */
  trait Unsafe {
    def env(name: String): Option[String]
    def property(name: String): Option[String]
  }

  /** Parser trait for converting strings to various types.
    *
    * This trait defines a method to parse a string value into a specific type. It is used by the
    * `System` effect to convert environment variables and system properties into the desired type.
    *
    * The available parsers include:
    *   - `String`
    *   - `Int`
    *   - `Long`
    *   - `Double`
    *   - `Float`
    *   - `Short`
    *   - `Byte`
    *   - `Boolean`
    *   - `Char`
    *
    * @tparam E
    *   The type of error that can occur during parsing.
    * @tparam A
    *   The type to which the string value should be parsed.
    */
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

    given Parser[NumberFormatException, Long] with {
      def parse(value: String): Either[NumberFormatException, Long] =
        try {
          Right(value.toLong)
        } catch {
          case e: NumberFormatException => Left(e)
        }
    }

    given Parser[NumberFormatException, Double] with {
      def parse(value: String): Either[NumberFormatException, Double] =
        try {
          Right(value.toDouble)
        } catch {
          case e: NumberFormatException => Left(e)
        }
    }

    given Parser[NumberFormatException, Float] with {
      def parse(value: String): Either[NumberFormatException, Float] =
        try {
          Right(value.toFloat)
        } catch {
          case e: NumberFormatException => Left(e)
        }
    }

    given Parser[NumberFormatException, Short] with {
      def parse(value: String): Either[NumberFormatException, Short] =
        try {
          Right(value.toShort)
        } catch {
          case e: NumberFormatException => Left(e)
        }
    }

    given Parser[NumberFormatException, Byte] with {
      def parse(value: String): Either[NumberFormatException, Byte] =
        try {
          Right(value.toByte)
        } catch {
          case e: NumberFormatException => Left(e)
        }
    }

    given Parser[IllegalArgumentException, Char] with {
      def parse(value: String): Either[IllegalArgumentException, Char] =
        if (value.length == 1) Right(value.charAt(0))
        else Left(new IllegalArgumentException("String must have exactly one character"))
    }
  }
}
