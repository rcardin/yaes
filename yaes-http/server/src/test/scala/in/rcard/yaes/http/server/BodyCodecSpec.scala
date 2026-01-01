package in.rcard.yaes.http.server


import in.rcard.yaes.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class BodyCodecSpec extends AnyFlatSpec with Matchers {

  "BodyCodec[String]" should "encode and decode strings" in {
    val codec = summon[BodyCodec[String]]
    codec.encode("hello") shouldBe "hello"

    val result = Raise.either {
      codec.decode("world")
    }
    result shouldBe Right("world")
    codec.contentType shouldBe "text/plain; charset=UTF-8"
  }

  "BodyCodec[Int]" should "encode integers to strings" in {
    val codec = summon[BodyCodec[Int]]
    codec.encode(42) shouldBe "42"
    codec.contentType shouldBe "text/plain; charset=UTF-8"
  }

  it should "decode valid integer strings" in {
    val codec = summon[BodyCodec[Int]]
    val result = Raise.either {
      codec.decode("123")
    }
    result shouldBe Right(123)
  }

  it should "raise DecodingError for invalid integers" in {
    val codec = summon[BodyCodec[Int]]
    val result = Raise.either {
      codec.decode("not a number")
    }
    result.isLeft shouldBe true
    result.left.get shouldBe a[DecodingError.ParseError]
  }

  "BodyCodec[Long]" should "encode longs to strings" in {
    val codec = summon[BodyCodec[Long]]
    codec.encode(123456789L) shouldBe "123456789"
    codec.contentType shouldBe "text/plain; charset=UTF-8"
  }

  it should "decode valid long strings" in {
    val codec = summon[BodyCodec[Long]]
    val result = Raise.either {
      codec.decode("987654321")
    }
    result shouldBe Right(987654321L)
  }

  it should "raise DecodingError for invalid longs" in {
    val codec = summon[BodyCodec[Long]]
    val result = Raise.either {
      codec.decode("not a long")
    }
    result.isLeft shouldBe true
    result.left.get shouldBe a[DecodingError.ParseError]
  }

  "BodyCodec[Double]" should "encode doubles to strings" in {
    val codec = summon[BodyCodec[Double]]
    codec.encode(3.14) shouldBe "3.14"
    codec.contentType shouldBe "text/plain; charset=UTF-8"
  }

  it should "decode valid double strings" in {
    val codec = summon[BodyCodec[Double]]
    val result = Raise.either {
      codec.decode("2.718")
    }
    result shouldBe Right(2.718)
  }

  it should "raise DecodingError for invalid doubles" in {
    val codec = summon[BodyCodec[Double]]
    val result = Raise.either {
      codec.decode("not a double")
    }
    result.isLeft shouldBe true
    result.left.get shouldBe a[DecodingError.ParseError]
  }

  "BodyCodec[Boolean]" should "encode booleans to strings" in {
    val codec = summon[BodyCodec[Boolean]]
    codec.encode(true) shouldBe "true"
    codec.encode(false) shouldBe "false"
    codec.contentType shouldBe "text/plain; charset=UTF-8"
  }

  it should "decode valid boolean strings" in {
    val codec = summon[BodyCodec[Boolean]]
    val resultTrue = Raise.either {
      codec.decode("true")
    }
    val resultFalse = Raise.either {
      codec.decode("false")
    }
    resultTrue shouldBe Right(true)
    resultFalse shouldBe Right(false)
  }

  it should "raise DecodingError for invalid booleans" in {
    val codec = summon[BodyCodec[Boolean]]
    val result = Raise.either {
      codec.decode("not a boolean")
    }
    result.isLeft shouldBe true
    result.left.get shouldBe a[DecodingError.ParseError]
  }
}
