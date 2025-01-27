package in.rcard.yaes.core

import org.scalatest.flatspec.AnyFlatSpec
import in.rcard.yaes.core.Experiments.ConsoleAlg
import in.rcard.yaes.core.Experiments.handleYeas
import in.rcard.yaes.core.Experiments.consoleHandler
import in.rcard.yaes.core.Experiments.Yeas
import in.rcard.yaes.core.Experiments.YConsole
import in.rcard.yaes.core.Experiments.PrintLn

class ExperimentsSpec extends AnyFlatSpec {
  "handleConsole" should "print a message" in {
    val program = Yeas.FlatMap(
      Yeas.Embed(YConsole.printLn("What's your name?")),
      _ =>
        Yeas.FlatMap(
          Yeas.Embed(YConsole.readLn()),
          name => Yeas.Embed(PrintLn(s"Hello $name!"))
        )
    )

    handleYeas(program, consoleHandler)
  }
}
