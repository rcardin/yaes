package in.rcard.yaes.http.server

import in.rcard.yaes.Async.Deadline
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.*

class ServerConfigSpec extends AnyFlatSpec with Matchers {

  "ServerConfig" should "have default port 8080" in {
    val config = ServerConfig()
    config.port shouldBe 8080
  }

  it should "have default deadline of 30 seconds" in {
    val config = ServerConfig()
    config.deadline shouldBe Deadline.after(30.seconds)
  }

  it should "have default maxBodySize of 1 MB" in {
    val config = ServerConfig()
    config.maxBodySize shouldBe 1048576 // 1 * 1024 * 1024
  }

  it should "have default maxHeaderSize of 16 KB" in {
    val config = ServerConfig()
    config.maxHeaderSize shouldBe 16384 // 16 * 1024
  }

  it should "allow custom port" in {
    val config = ServerConfig(port = 9000)
    config.port shouldBe 9000
  }

  it should "allow custom deadline" in {
    val customDeadline = Deadline.after(60.seconds)
    val config = ServerConfig(deadline = customDeadline)
    config.deadline shouldBe customDeadline
  }

  it should "allow custom maxBodySize" in {
    val config = ServerConfig(maxBodySize = 2097152)
    config.maxBodySize shouldBe 2097152
  }

  it should "allow custom maxHeaderSize" in {
    val config = ServerConfig(maxHeaderSize = 32768)
    config.maxHeaderSize shouldBe 32768
  }

  "Size DSL" should "convert bytes correctly" in {
    100.bytes shouldBe 100
  }

  it should "convert kilobytes correctly" in {
    1.kilobytes shouldBe 1024
    16.kilobytes shouldBe 16384
  }

  it should "convert megabytes correctly" in {
    1.megabytes shouldBe 1048576
    5.megabytes shouldBe 5242880
  }

  it should "allow using size DSL in ServerConfig" in {
    val config = ServerConfig(
      maxBodySize = 5.megabytes,
      maxHeaderSize = 32.kilobytes
    )
    config.maxBodySize shouldBe 5242880
    config.maxHeaderSize shouldBe 32768
  }

  it should "allow combining all custom parameters" in {
    val config = ServerConfig(
      port = 9000,
      deadline = Deadline.after(60.seconds),
      maxBodySize = 10.megabytes,
      maxHeaderSize = 64.kilobytes
    )
    config.port shouldBe 9000
    config.deadline shouldBe Deadline.after(60.seconds)
    config.maxBodySize shouldBe 10485760
    config.maxHeaderSize shouldBe 65536
  }
}
