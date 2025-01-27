package in.rcard.yaes.core

import scala.io.StdIn
import in.rcard.yaes.core.Experiments.ConsoleAlg

object Experiments {

  type ConsoleAlg[A] = Console.type ?=> A
  // sealed trait ConsoleAlg[A]
  case class ReadLn()                   extends ConsoleAlg[String]
  case class PrintLn[E, R](text: String) extends ConsoleAlg[Unit]

  object YConsole {
    def readLn(): ConsoleAlg[String] = ReadLn() 
    def printLn(text: String): ConsoleAlg[Unit] = PrintLn(text)
  }

  def handleConsole[A](effect: ConsoleAlg[A]): A = {
    effect match {
      case PrintLn(text) => println(text)
      case ReadLn()      => StdIn.readLine()
    }
  }

  trait Handler[Op[_]] {
    def handle[A](op: Op[A]): A
    def canHandle(op: Any): Boolean
  }

  val consoleHandler = new Handler[ConsoleAlg] {
    override def handle[A](op: ConsoleAlg[A]): A = op match {
      case PrintLn(text) => println(text)
      case ReadLn()      => StdIn.readLine()
    }
    override def canHandle(op: Any): Boolean = op.isInstanceOf[ConsoleAlg[?]]
  }

  enum Yeas[A] {
    case Pure(value: A)                               extends Yeas[A]
    case FlatMap[A, B](sub: Yeas[A], f: A => Yeas[B]) extends Yeas[B]
    case Embed[Op[_], A](op: Op[A])                   extends Yeas[A]

    // def flatMap[B, Op[_]](f: A => Op[B]): Yeas[B] = Yeas.FlatMap(Embed(this), f)
  }

  def handleYeas[A, Op[_]](yeas: Yeas[A], handler: Handler[Op]): Yeas[A] = {
    yeas match {
      case Yeas.Embed(op) if handler.canHandle(op) =>
        Yeas.Pure(handler.handle(op.asInstanceOf[Op[A]]))
      case Yeas.Embed(op)   => Yeas.Embed(op)
      case Yeas.Pure(value) => Yeas.Pure(value)
      case Yeas.FlatMap(embed: Yeas.Embed[Op, ?], f) if handler.canHandle(embed.op) =>
        val x = handler.handle(embed.op)
        handleYeas(f(x), handler)
      case Yeas.FlatMap(embed, f) => Yeas.FlatMap(embed, x => handleYeas(f(x), handler))
    }
  }

  @main def run(): Unit = {
    val program = Yeas.FlatMap(
      Yeas.Embed(PrintLn("What's your name?")),
      _ => Yeas.Embed(ReadLn())
    )

    handleYeas(program, consoleHandler)

    // val program = for {
    //   _    <- ConsoleAlg.PrintLn("What's your name?")
    //   name <- ConsoleAlg.ReadLn()
    //   _    <- ConsoleAlg.PrintLn(s"Hello, $name!")
    // } yield ()
  }
}
