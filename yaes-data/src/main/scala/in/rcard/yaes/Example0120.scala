package in.rcard.yaes

import in.rcard.yaes.System.*

import in.rcard.yaes.Clock.*

import in.rcard.yaes.Input.*

import in.rcard.yaes.Random.*
import in.rcard.yaes.Output.*

import in.rcard.yaes.Log.*
import scala.concurrent.duration.DurationInt
import in.rcard.yaes.Async.Deadline.*
import in.rcard.yaes.Channel.Producer

object HelloWorld extends YaesApp {

  override def run = {
    Shutdown.run {
      Raise.ignore {
        Async.withGracefulShutdown(deadline = after(10.seconds)) {
          val channel = Channel.produce[String] {
            while (true) {
              Producer.send("Hello World 🌍")
              Async.delay(500.millis)
            }
          }

          Async.fork {
            Async.delay(2.seconds)
            Shutdown.initiateShutdown()
          }

          for (value <- channel) {
            Output.printLn(value)
          }
        }
      }
    }
  }
}
