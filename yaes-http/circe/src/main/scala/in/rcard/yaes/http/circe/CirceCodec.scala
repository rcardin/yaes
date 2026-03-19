package in.rcard.yaes.http.circe

import in.rcard.yaes.*
import in.rcard.yaes.http.core.{BodyCodec, DecodingError}
import io.circe.{Encoder, Decoder, ParsingFailure, DecodingFailure}
import io.circe.syntax.*
import io.circe.parser.decode as circeDecode

/** 
 * Default `BodyCodec` instance for any type `A` that has both a Circe [[io.circe.Encoder]]
 * and [[io.circe.Decoder]] in scope.
 *
 * This codec:
 *   - Encodes values of type `A` as compact JSON using Circe (`asJson.noSpaces`).
 *   - Sets the HTTP `Content-Type` header to `application/json`.
 *   - Decodes JSON bodies using Circe's `decode`, mapping failures to:
 *     - If Circe returns a [[io.circe.ParsingFailure]] (invalid JSON syntax), it is mapped to
 *       [[in.rcard.yaes.http.core.DecodingError.ParseError]] with the original message and
 *       exception attached.
 *     - If Circe returns a [[io.circe.DecodingFailure]] (valid JSON but wrong shape, e.g. missing
 *       fields), it is mapped to [[in.rcard.yaes.http.core.DecodingError.ValidationError]]
 *       with the original message.
 * Usage:
 * {{{
 *   import io.circe.{Encoder, Decoder}
 *   import in.rcard.yaes.http.circe.given
 *
 *   final case class MyPayload(value: String)
 *
 *   given Encoder[MyPayload] = ???
 *   given Decoder[MyPayload] = ???
 *
 *   // `circeBodyCodec` provides an implicit BodyCodec[MyPayload]
 *   def handleBody(body: String)(using codec: BodyCodec[MyPayload]) = {
 *     val decoded: MyPayload raises DecodingError = codec.decode(body)
 *   }
 * }}}
 *
 * Error mapping:
 *   - If Circe successfully decodes the JSON, the resulting value of type `A` is returned.
 *   - If Circe returns a [[io.circe.ParsingFailure]] (invalid JSON syntax), it is mapped to
 *     [[in.rcard.yaes.http.core.DecodingError.ParseError]] with the original message and
 *     exception attached.
 *   - If Circe returns a [[io.circe.DecodingFailure]] (valid JSON but wrong shape, e.g. missing
 *     fields), it is mapped to [[in.rcard.yaes.http.core.DecodingError.ValidationError]]
 *     with the original message.
 */
given circeBodyCodec[A](using encoder: Encoder[A], decoder: Decoder[A]): BodyCodec[A] with {
  def contentType: String = "application/json"

  def encode(value: A): String = value.asJson.noSpaces

  def decode(body: String): A raises DecodingError =
    circeDecode[A](body) match {
      case Right(value) => value
      case Left(error: ParsingFailure) =>
        Raise.raise(DecodingError.ParseError(error.getMessage, Some(error)))
      case Left(error: DecodingFailure) =>
        Raise.raise(DecodingError.ValidationError(error.getMessage))
    }
}
