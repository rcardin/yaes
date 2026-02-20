package in.rcard.yaes.http.circe

import in.rcard.yaes.*
import in.rcard.yaes.http.server.{BodyCodec, DecodingError}
import io.circe.{Encoder, Decoder}
import io.circe.syntax.*
import io.circe.parser.decode as circeDecode

given circeBodyCodec[A](using encoder: Encoder[A], decoder: Decoder[A]): BodyCodec[A] with {
  def contentType: String = "application/json"

  def encode(value: A): String = value.asJson.noSpaces

  def decode(body: String): A raises DecodingError =
    circeDecode[A](body) match {
      case Right(value) => value
      case Left(error) =>
        Raise.raise(DecodingError.ParseError(error.getMessage, Some(error)))
    }
}
