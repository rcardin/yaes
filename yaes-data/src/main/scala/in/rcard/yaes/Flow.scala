package in.rcard.yaes

/** A Flow is a cold asynchronous data stream that sequentially emits values and completes normally
  * or with an exception.
  *
  * Flows are conceptually similar to Iterators from the Collections framework but emit items
  * asynchronously. The main differences between a Flow and an Iterator are:
  *   - Flows can emit values asynchronously
  *   - Flow emissions can be transformed with various operators
  *   - Flow emissions can be observed through the `collect` method
  *
  * Example:
  * {{{
  * // Creating and collecting a flow
  * val flow = Flow.flow[Int] {
  *   Flow.emit(1)
  *   Flow.emit(2)
  *   Flow.emit(3)
  * }
  *
  * // Collecting values from the flow
  * val result = scala.collection.mutable.ArrayBuffer[Int]()
  * flow.collect { value =>
  *   result += value
  * }
  * // result contains: 1, 2, 3
  * }}}
  *
  * @tparam A
  *   The type of values emitted by this flow
  */
trait Flow[A] {

  /** Collects values from this Flow using the given collector. This is a terminal operator that
    * starts collecting the flow.
    *
    * Example:
    * {{{
    * val flow = Flow(1, 2, 3)
    *
    * val numbers = scala.collection.mutable.ArrayBuffer[Int]()
    * flow.collect { value =>
    *   numbers += value
    * }
    * // numbers contains: 1, 2, 3
    * }}}
    *
    * @param collector
    *   The collector that will accumulate values from the flow
    */
  def collect(collector: Flow.FlowCollector[A]): Unit
}

object Flow {

  /** A collector interface for a Flow. This interface is used to accept values emitted by a Flow.
    *
    * Example:
    * {{{
    * // Creating a custom collector
    * val customCollector = new FlowCollector[Int] {
    *   override def emit(value: Int): Unit = {
    *     println(s"Collected value: $value")
    *   }
    * }
    *
    * Flow(1, 2, 3).collect(customCollector)
    * // Prints:
    * // Collected value: 1
    * // Collected value: 2
    * // Collected value: 3
    * }}}
    *
    * @tparam A
    *   The type of values this collector can accept
    */
  trait FlowCollector[A] {

    /** Accepts the given value and processes it.
      *
      * @param value
      *   The value to be processed
      */
    def emit(value: A): Unit
  }

  /** Extension method to convert a sequence to a flow.
    *
    * Example:
    * {{{
    * // Converting a list to a flow
    * val numbers = List(1, 2, 3)
    * val flow = numbers.asFlow()
    *
    * val result = scala.collection.mutable.ArrayBuffer[Int]()
    * flow.collect { value =>
    *   result += value
    * }
    * // result contains: 1, 2, 3
    * }}}
    *
    * @param seq
    *   The sequence to convert to a flow
    * @tparam A
    *   The type of elements in the sequence
    * @return
    *   A flow that emits all items from the original sequence
    */
  extension [A](seq: Seq[A])
    def asFlow(): Flow[A] = flow {
      seq.foreach(item => emit(item))
    }

  extension [A](originalFlow: Flow[A]) {

    /** Returns a flow that invokes the given action before this flow starts to be collected.
      *
      * Example:
      * {{{
      * val originalFlow = Flow(1, 2, 3)
      * val result = scala.collection.mutable.ArrayBuffer[Int]()
      *
      * originalFlow
      *   .onStart {
      *     Flow.emit(0) // Emit an extra value at the start
      *   }
      *   .collect { value =>
      *     result += value
      *   }
      * // result contains: 0, 1, 2, 3
      * }}}
      *
      * @param action
      *   The action to invoke
      * @return
      *   A flow that invokes the action before collecting from the original flow
      */
    def onStart(action: Flow.FlowCollector[A] ?=> Unit): Flow[A] = new Flow[A] {
      override def collect(collector: Flow.FlowCollector[A]): Unit = {
        given Flow.FlowCollector[A] = collector
        action
        originalFlow.collect(collector)
      }
    }

    /** Returns a flow that applies the given transform function to each value of the original flow.
      * The transform function can emit any number of values into the resulting flow for each input
      * value.
      *
      * Example:
      * {{{
      * val originalFlow = Flow(1, 2, 3)
      * val result = scala.collection.mutable.ArrayBuffer[String]()
      *
      * originalFlow
      *   .transform { value =>
      *     // Emit each value twice but as strings
      *     Flow.emit(value.toString)
      *     Flow.emit(value.toString)
      *   }
      *   .collect { value =>
      *     result += value
      *   }
      * // result contains: "1", "1", "2", "2", "3", "3"
      * }}}
      *
      * @param transform
      *   The transform function
      * @tparam B
      *   The type of values in the resulting flow
      * @return
      *   A flow that transforms the original flow using the specified transform function
      */
    def transform[B](transform: FlowCollector[B] ?=> A => Unit): Flow[B] = new Flow[B] {
      override def collect(collector: Flow.FlowCollector[B]): Unit = {
        given Flow.FlowCollector[B] = collector
        originalFlow.collect { value =>
          transform(value)
        }
      }
    }

    /** Returns a flow containing the original flow's elements and then applies the given action to
      * each emitted value. The original item is then re-emitted downstream.
      *
      * Example:
      * {{{
      * val originalFlow = Flow(1, 2, 3)
      * val result = scala.collection.mutable.ArrayBuffer[Int]()
      * val sideEffectValues = scala.collection.mutable.ArrayBuffer[Int]()
      *
      * originalFlow
      *   .onEach { value =>
      *     sideEffectValues += value * 10 // Side effect without changing the flow
      *   }
      *   .collect { value =>
      *     result += value
      *   }
      * // result contains: 1, 2, 3
      * // sideEffectValues contains: 10, 20, 30
      * }}}
      *
      * @param action
      *   The action to apply to each value
      * @return
      *   A flow that applies the given action to each value and emits the original value
      */
    def onEach(action: A => Unit): Flow[A] = originalFlow.transform { value =>
      action(value)
      Flow.emit(value)
    }

    /** Returns a flow containing the results of applying the given transform function to each value
      * of the original flow.
      *
      * Example:
      * {{{
      * val originalFlow = Flow(1, 2, 3)
      * val result = scala.collection.mutable.ArrayBuffer[String]()
      *
      * originalFlow
      *   .map { value =>
      *     value.toString // Transform each value to a string
      *   }
      *   .collect { value =>
      *     result += value
      *   }
      * // result contains: "1", "2", "3"
      * }}}
      *
      * @param transform
      *   The transform function
      * @tparam B
      *   The type of values in the resulting flow
      * @return
      *   A flow containing transformed values
      */
    def map[B](transform: A => B): Flow[B] = originalFlow.transform { value =>
      Flow.emit(transform(value))
    }

    /** Returns a flow containing only values from the original flow that match the given predicate.
      *
      * Example:
      * {{{
      * val originalFlow = Flow(1, 2, 3, 4, 5)
      * val result = scala.collection.mutable.ArrayBuffer[Int]()
      *
      * originalFlow
      *   .filter { value =>
      *     value % 2 == 0 // Only keep even numbers
      *   }
      *   .collect { value =>
      *     result += value
      *   }
      * // result contains: 2, 4
      * }}}
      *
      * @param predicate
      *   The predicate to test elements
      * @return
      *   A flow containing only matching elements
      */
    def filter(predicate: A => Boolean): Flow[A] = transform { value =>
      if (predicate(value)) {
        Flow.emit(value)
      }
    }

    /** Returns a flow that emits only the first n values from this flow. After n values are
      * emitted, the flow completes.
      *
      * Example:
      * {{{
      * val originalFlow = Flow(1, 2, 3, 4, 5)
      * val result = scala.collection.mutable.ArrayBuffer[Int]()
      *
      * originalFlow
      *   .take(3) // Take only the first 3 values
      *   .collect { value =>
      *     result += value
      *   }
      * // result contains: 1, 2, 3
      * }}}
      *
      * @param n
      *   The number of values to take
      * @return
      *   A flow containing only the first n values
      * @throws IllegalArgumentException
      *   if n is less than or equal to 0
      */
    def take(n: Int): Flow[A] =
      if (n <= 0) {
        throw new IllegalArgumentException("n must be greater than 0")
      }
      Flow.flow {
        var count = 0

        originalFlow.collect { value =>
          if (count < n) {
            count += 1
            Flow.emit(value)
          }
        }
      }

    /** Returns a flow that skips the first n values emitted by this flow and then emits the
      * remaining values.
      *
      * Example:
      * {{{
      * val originalFlow = Flow(1, 2, 3, 4, 5)
      * val result = scala.collection.mutable.ArrayBuffer[Int]()
      *
      * originalFlow
      *   .drop(2) // Skip the first 2 values
      *   .collect { value =>
      *     result += value
      *   }
      * // result contains: 3, 4, 5
      * }}}
      *
      * @param n
      *   The number of values to skip
      * @return
      *   A flow that skips the first n values and emits the remaining ones
      * @throws IllegalArgumentException
      *   if n is less than or equal to 0
      */
    def drop(n: Int): Flow[A] =
      if (n <= 0) {
        throw new IllegalArgumentException("n must be greater than 0")
      }
      Flow.flow {
        var skipped = 0
        originalFlow.collect { value =>
          if (skipped < n) {
            skipped += 1
          } else {
            Flow.emit(value)
          }
        }
      }

    /** Accumulates the values of this flow using the given operation, starting with the given
      * initial value. This is a terminal operator that processes all elements emitted by the flow.
      *
      * Example:
      * {{{
      * val originalFlow = Flow(1, 2, 3, 4, 5)
      *
      * val sum = originalFlow.fold(0) { (acc, value) =>
      *   acc + value // Sum all values
      * }
      * // sum = 15
      *
      * val concatenated = originalFlow.fold("") { (acc, value) =>
      *   acc + value.toString // Concatenate all values as a string
      * }
      * // concatenated = "12345"
      * }}}
      *
      * @param initial
      *   The initial accumulator value
      * @param operation
      *   The operation that takes the current accumulator value and a new value from the flow and
      *   calculates a new accumulator value
      * @tparam R
      *   The type of the accumulator value
      * @return
      *   The final accumulator value
      */
    def fold[R](initial: R)(operation: (R, A) => R): R = {
      var result = initial
      originalFlow.collect { value =>
        result = operation(result, value)
      }
      result
    }

    /** Counts the number of values emitted by this flow. This is a terminal operator that processes
      * all elements emitted by the flow.
      *
      * Example:
      * {{{
      * val originalFlow = Flow(1, 2, 3, 4, 5)
      *
      * val count = originalFlow.count() // Count the number of values
      * // count = 5
      *
      * val filteredCount = originalFlow
      *   .filter { value => value % 2 == 0 }
      *   .count() // Count only even values
      * // filteredCount = 2
      * }}}
      *
      * @return
      *   The count of emitted values
      */
    def count(): Int = {
      var count = 0
      originalFlow.collect { _ =>
        count += 1
      }
      count
    }

    /** Returns a flow that pairs each element of the original flow with its index beginning at 0
      *
      * Example:
      * {{{
      * val originalFlow = Flow("a", "b", "c")
      * val result = scala.collection.mutable.ArrayBuffer[(String, Long)]()
      *
      * originalFlow
      *   .zipWithIndex()
      *   .collect { value =>
      *     result += value
      *   }
      * // result contains: ("a", 0), ("b", 1), ("c", 2)
      * }}}
      *
      * @return
      *   A flow that pairs each element of the original flow with its index beginning at 0
      */
    def zipWithIndex(): Flow[(A, Long)] = Flow.flow {
      var index: Long = 0L
      originalFlow.collect { a =>
        Flow.emit((a, index))
        index += 1
      }
    }

  }

  extension (stringFlow: Flow[String]) {

    /** Encodes strings from this flow into UTF-8 byte arrays. Each string is encoded separately
      * and emitted as a separate byte array.
      *
      * The method uses a CharsetEncoder configured to report malformed input and unmappable
      * characters. If unmappable characters are encountered, the flow will throw a
      * `java.nio.charset.UnmappableCharacterException`.
      *
      * Example:
      * {{{
      * import scala.collection.mutable.ArrayBuffer
      * import java.nio.charset.StandardCharsets
      *
      * // Encoding simple text
      * val flow = Flow("Hello", "World")
      * val result = ArrayBuffer[Array[Byte]]()
      *
      * flow.encodeToUtf8().collect { bytes =>
      *   result += bytes
      * }
      * // result contains byte arrays for each string
      *
      * // Encoding with multi-byte characters
      * val utf8Flow = Flow("Hello 世界! 😀")
      * val encoded = utf8Flow.encodeToUtf8().fold(Array.empty[Byte])(_ ++ _)
      * val decoded = new String(encoded, StandardCharsets.UTF_8)
      * // decoded == "Hello 世界! 😀"
      * }}}
      *
      * @return
      *   A flow that emits UTF-8 encoded byte arrays
      */
    def encodeToUtf8(): Flow[Array[Byte]] = {
      encodeTo(java.nio.charset.StandardCharsets.UTF_8)
    }

    /** Encodes strings from this flow into byte arrays using the specified charset. Each string is
      * encoded separately and emitted as a separate byte array.
      *
      * The method uses a CharsetEncoder configured to report malformed input and unmappable
      * characters. If unmappable characters are encountered for the specified charset, the flow
      * will throw a `java.nio.charset.UnmappableCharacterException`.
      *
      * '''Buffer Allocation:''' The buffer size is calculated to accommodate both the encoded
      * string content and any charset-specific overhead such as Byte Order Marks (BOM). For empty
      * strings or very short strings, a minimum buffer size of `maxBytesPerChar * 4` is allocated
      * to ensure sufficient space for BOMs (UTF-16: 2 bytes, UTF-8 BOM: 3 bytes) and other
      * charset preambles, with a safety margin to handle various charset implementations.
      *
      * Example:
      * {{{
      * import scala.collection.mutable.ArrayBuffer
      * import java.nio.charset.StandardCharsets
      *
      * // Encoding with UTF-16
      * val flow = Flow("Hello", "World")
      * val result = ArrayBuffer[Array[Byte]]()
      *
      * flow.encodeTo(StandardCharsets.UTF_16).collect { bytes =>
      *   result += bytes
      * }
      * // result contains UTF-16 encoded byte arrays
      *
      * // Encoding with ISO-8859-1
      * val isoFlow = Flow("café")
      * val encoded = isoFlow.encodeTo(StandardCharsets.ISO_8859_1)
      *   .fold(Array.empty[Byte])(_ ++ _)
      * val decoded = new String(encoded, StandardCharsets.ISO_8859_1)
      * // decoded == "café"
      * }}}
      *
      * @param charset
      *   The charset to use for encoding
      * @return
      *   A flow that emits encoded byte arrays
      */
    def encodeTo(charset: java.nio.charset.Charset): Flow[Array[Byte]] = flow {
      val encoder = charset
        .newEncoder()
        .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
        .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)

      val minBomBufferMultiplier = 4

      stringFlow.collect { str =>
        val inputBuffer = java.nio.CharBuffer.wrap(str)
        val minBufferSize = Math.max(
          (str.length * encoder.maxBytesPerChar()).toInt,
          encoder.maxBytesPerChar().toInt * minBomBufferMultiplier
        )
        val outputBuffer = java.nio.ByteBuffer.allocate(minBufferSize)

        val encodeResult = encoder.encode(inputBuffer, outputBuffer, true)
        if (encodeResult.isError) {
          encodeResult.throwException()
        }

        val flushResult = encoder.flush(outputBuffer)
        if (flushResult.isError) {
          flushResult.throwException()
        }

        outputBuffer.flip()
        val bytes = new Array[Byte](outputBuffer.remaining())
        outputBuffer.get(bytes)

        encoder.reset()

        emit(bytes)
      }
    }
  }

  extension (byteFlow: Flow[Array[Byte]]) {

    /** Decodes byte arrays from this flow into UTF-8 strings. This method correctly handles
      * multi-byte UTF-8 character sequences that may be split across chunk boundaries.
      *
      * The method uses a CharsetDecoder to properly buffer incomplete character sequences and emit
      * them when complete. This ensures that characters are never corrupted when reading data in
      * chunks from streams.
      *
      * Example:
      * {{{
      * import java.io.FileInputStream
      * import scala.collection.mutable.ArrayBuffer
      * import scala.util.Using
      *
      * // Reading a UTF-8 text file
      * Using(new FileInputStream("data.txt")) { inputStream =>
      *   val result = ArrayBuffer[String]()
      *   Flow.fromInputStream(inputStream, bufferSize = 1024)
      *     .asUtf8String()
      *     .collect { str =>
      *       result += str
      *     }
      *   // result contains decoded strings
      * }
      *
      * // Processing JSON from a network socket
      * val jsonBytes = """{"name":"世界","emoji":"😀"}""".getBytes("UTF-8")
      * val input = new java.io.ByteArrayInputStream(jsonBytes)
      * val json = Flow.fromInputStream(input, bufferSize = 5)
      *   .asUtf8String()
      *   .fold("")(_ + _)
      * // json contains the complete, correctly decoded JSON string
      * }}}
      *
      * @return
      *   A flow that emits decoded UTF-8 strings
      */
    def asUtf8String(): Flow[String] = {
      asString(java.nio.charset.StandardCharsets.UTF_8)
    }

    /** Decodes byte arrays from this flow into strings using the specified charset. This method
      * correctly handles multi-byte character sequences that may be split across chunk boundaries.
      *
      * The method uses a CharsetDecoder to properly buffer incomplete character sequences and emit
      * them when complete. This ensures that characters are never corrupted when reading data in
      * chunks from streams.
      *
      * '''Error Handling:''' The decoder is configured to report malformed input and unmappable
      * characters. If malformed or invalid byte sequences are encountered, the flow will throw a
      * `java.nio.charset.MalformedInputException` or
      * `java.nio.charset.UnmappableCharacterException`. Any valid data decoded before the error
      * will be emitted before the exception is thrown.
      *
      * Example:
      * {{{
      * import java.io.ByteArrayInputStream
      * import java.nio.charset.StandardCharsets
      * import scala.collection.mutable.ArrayBuffer
      *
      * // Reading ISO-8859-1 encoded data
      * val data = "café".getBytes(StandardCharsets.ISO_8859_1)
      * val input = new ByteArrayInputStream(data)
      * val result = ArrayBuffer[String]()
      *
      * Flow.fromInputStream(input, bufferSize = 2)
      *   .asString(StandardCharsets.ISO_8859_1)
      *   .collect { str =>
      *     result += str
      *   }
      * // result contains correctly decoded strings
      *
      * // Reading UTF-16 data
      * val utf16Data = "Hello 世界".getBytes(StandardCharsets.UTF_16)
      * val utf16Input = new ByteArrayInputStream(utf16Data)
      * val utf16Result = Flow.fromInputStream(utf16Input)
      *   .asString(StandardCharsets.UTF_16)
      *   .fold("")(_ + _)
      * // utf16Result contains the complete decoded string
      * }}}
      *
      * @param charset
      *   The charset to use for decoding
      * @return
      *   A flow that emits decoded strings
      */
    def asString(charset: java.nio.charset.Charset): Flow[String] = flow {

      val decoder = charset
        .newDecoder()
        .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
        .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)

      var incompleteBytes = Array.empty[Byte]

      byteFlow.collect { bytes =>
        val fullBytes   = incompleteBytes ++ bytes
        val inputBuffer = java.nio.ByteBuffer.wrap(fullBytes)
        val outputBuffer = java.nio.CharBuffer.allocate(fullBytes.length)

        val result = decoder.decode(inputBuffer, outputBuffer, false)
        if (result.isError) {
          result.throwException()
        }

        if (inputBuffer.hasRemaining) {
          val remaining = new Array[Byte](inputBuffer.remaining())
          inputBuffer.get(remaining)
          incompleteBytes = remaining
        } else {
          incompleteBytes = Array.empty[Byte]
        }

        outputBuffer.flip()
        if (outputBuffer.hasRemaining) {
          emit(outputBuffer.toString)
        }
      }

      if (incompleteBytes.nonEmpty) {
        val inputBuffer  = java.nio.ByteBuffer.wrap(incompleteBytes)
        val outputBuffer = java.nio.CharBuffer.allocate(incompleteBytes.length)
        val decodeResult = decoder.decode(inputBuffer, outputBuffer, true)
        if (decodeResult.isError) {
          decodeResult.throwException()
        }
        val flushResult = decoder.flush(outputBuffer)
        if (flushResult.isError) {
          flushResult.throwException()
        }
        outputBuffer.flip()
        if (outputBuffer.hasRemaining) {
          emit(outputBuffer.toString)
        }
      }
    }

    /** Writes all byte arrays from this flow to the given OutputStream. This is a terminal
      * operator that processes all elements emitted by the flow.
      *
      * Empty byte arrays are skipped and not written to the stream. After all data is written, the
      * stream is flushed once. The stream is NOT closed - the caller is responsible for managing
      * the stream lifecycle.
      *
      * Example:
      * {{{
      * import java.io.FileOutputStream
      * import scala.util.Using
      *
      * // Writing binary data to a file
      * val data = Array[Byte](1, 2, 3, 4, 5)
      * val flow = Flow(data)
      *
      * Using(new FileOutputStream("output.bin")) { outputStream =>
      *   flow.toOutputStream(outputStream)
      * }
      *
      * // Writing encoded strings to a file
      * val strings = List("Hello", " ", "World", "!")
      * Using(new FileOutputStream("output.txt")) { outputStream =>
      *   Flow(strings*)
      *     .encodeToUtf8()
      *     .toOutputStream(outputStream)
      * }
      *
      * // Writing to a ByteArrayOutputStream
      * val output = new java.io.ByteArrayOutputStream()
      * Flow("Test".getBytes()).toOutputStream(output)
      * val result = output.toByteArray
      * }}}
      *
      * @param outputStream
      *   The OutputStream to write data to
      * @throws java.io.IOException
      *   if an I/O error occurs during writing or flushing
      */
    def toOutputStream(outputStream: java.io.OutputStream): Unit = {
      byteFlow.collect { bytes =>
        if (bytes.nonEmpty) {
          outputStream.write(bytes)
        }
      }
      outputStream.flush()
    }

    /** Writes all byte arrays from this flow to a file at the given path. This is a terminal
      * operator that processes all elements emitted by the flow.
      *
      * Empty byte arrays are skipped and not written to the file. The method automatically manages
      * the OutputStream lifecycle - the stream is opened when the method is called and is
      * automatically closed when writing completes (either successfully or due to an exception).
      *
      * If the file already exists, it will be overwritten. If parent directories don't exist, they
      * will be created automatically.
      *
      * Example:
      * {{{
      * import java.nio.file.Paths
      * import scala.collection.mutable.ArrayBuffer
      *
      * // Writing binary data to a file
      * val data = Array[Byte](1, 2, 3, 4, 5)
      * val flow = Flow(data)
      * val path = Paths.get("output.bin")
      * flow.toFile(path)
      *
      * // Writing encoded strings to a file
      * val strings = List("Hello", " ", "World", "!")
      * Flow(strings*)
      *   .encodeToUtf8()
      *   .toFile(Paths.get("output.txt"))
      *
      * // Writing lines to a file
      * val lines = List("Line 1", "Line 2", "Line 3")
      * Flow(lines*)
      *   .map(line => (line + "\n").getBytes("UTF-8"))
      *   .toFile(Paths.get("lines.txt"))
      *
      * // Copying a file
      * val sourcePath = Paths.get("source.txt")
      * val destPath = Paths.get("destination.txt")
      * Flow.fromFile(sourcePath).toFile(destPath)
      * }}}
      *
      * @param path
      *   The path to the file to write data to
      * @throws java.io.IOException
      *   if an I/O error occurs during writing, if the file exists but is a directory, or if the
      *   file cannot be created or opened for writing. The exception message includes the file
      *   path for context.
      */
    def toFile(path: java.nio.file.Path): Unit = {
      var outputStream: java.io.OutputStream = null
      try {
        // Create parent directories if they don't exist
        val parent = path.getParent
        if (parent != null && !java.nio.file.Files.exists(parent)) {
          java.nio.file.Files.createDirectories(parent)
        }

        outputStream = java.nio.file.Files.newOutputStream(path)
        toOutputStream(outputStream)
      } catch {
        case e: java.io.IOException =>
          throw new java.io.IOException(s"Failed to write to file: $path", e)
      } finally {
        if (outputStream != null) {
          try {
            outputStream.close()
          } catch {
            case _: java.io.IOException => // Ignore close exceptions
          }
        }
      }
    }

    /** Decodes byte arrays from this flow into UTF-8 strings and splits them into lines. This
      * method correctly handles multi-byte UTF-8 character sequences and line separators that may
      * be split across chunk boundaries.
      *
      * The method recognizes all common line separators: LF (`\n`), CRLF (`\r\n`), and CR (`\r`).
      * Line separators are removed from the emitted strings. Empty lines are preserved.
      *
      * If the stream ends without a trailing line separator, the last line is still emitted. If
      * the stream is empty, no lines are emitted.
      *
      * Example:
      * {{{
      * import java.io.FileInputStream
      * import scala.collection.mutable.ArrayBuffer
      * import scala.util.Using
      *
      * // Reading lines from a text file
      * Using(new FileInputStream("data.txt")) { inputStream =>
      *   val lines = ArrayBuffer[String]()
      *   Flow.fromInputStream(inputStream, bufferSize = 1024)
      *     .linesInUtf8()
      *     .collect { line =>
      *       lines += line
      *     }
      *   // lines contains all lines from the file
      * }
      *
      * // Processing CSV data line by line
      * val csvData = "name,age\nAlice,30\nBob,25\n".getBytes("UTF-8")
      * val input = new java.io.ByteArrayInputStream(csvData)
      * Flow.fromInputStream(input)
      *   .linesInUtf8()
      *   .filter(_.nonEmpty)
      *   .map(_.split(","))
      *   .collect { fields =>
      *     println(s"Name: ${fields(0)}, Age: ${fields(1)}")
      *   }
      * }}}
      *
      * @return
      *   A flow that emits decoded UTF-8 strings split into lines
      * @throws java.nio.charset.MalformedInputException
      *   if malformed UTF-8 byte sequences are encountered
      */
    def linesInUtf8(): Flow[String] = {
      linesIn(java.nio.charset.StandardCharsets.UTF_8)
    }

    /** Decodes byte arrays from this flow into strings using the specified charset and splits them
      * into lines. This method correctly handles multi-byte character sequences and line separators
      * that may be split across chunk boundaries.
      *
      * The method recognizes all common line separators: LF (`\n`), CRLF (`\r\n`), and CR (`\r`).
      * Line separators are removed from the emitted strings. Empty lines are preserved.
      *
      * If the stream ends without a trailing line separator, the last line is still emitted. If
      * the stream is empty, no lines are emitted.
      *
      * '''Error Handling:''' The decoder is configured to report malformed input and unmappable
      * characters. If malformed or invalid byte sequences are encountered, the flow will throw a
      * `java.nio.charset.MalformedInputException` or
      * `java.nio.charset.UnmappableCharacterException`. Any valid lines decoded before the error
      * will be emitted before the exception is thrown.
      *
      * Example:
      * {{{
      * import java.io.ByteArrayInputStream
      * import java.nio.charset.StandardCharsets
      * import scala.collection.mutable.ArrayBuffer
      *
      * // Reading ISO-8859-1 encoded file
      * val data = "café\nlatte\n".getBytes(StandardCharsets.ISO_8859_1)
      * val input = new ByteArrayInputStream(data)
      * val lines = ArrayBuffer[String]()
      *
      * Flow.fromInputStream(input, bufferSize = 1024)
      *   .linesIn(StandardCharsets.ISO_8859_1)
      *   .collect { line =>
      *     lines += line
      *   }
      * // lines contains: "café", "latte"
      *
      * // Reading UTF-16 data
      * val utf16Data = "Hello\nWorld\n".getBytes(StandardCharsets.UTF_16)
      * val utf16Input = new ByteArrayInputStream(utf16Data)
      * Flow.fromInputStream(utf16Input)
      *   .linesIn(StandardCharsets.UTF_16)
      *   .collect { line =>
      *     println(line)
      *   }
      * }}}
      *
      * @param charset
      *   The charset to use for decoding
      * @return
      *   A flow that emits decoded strings split into lines
      */
    def linesIn(charset: java.nio.charset.Charset): Flow[String] = flow {
      var lineBuffer    = new StringBuilder()
      var lastCharWasCR = false

      byteFlow.asString(charset).collect { chunk =>
        chunk.foreach { ch =>
          if (lastCharWasCR) {
            if (ch == '\n') {
              lastCharWasCR = false
            } else if (ch == '\r') {
              emit("")
              lastCharWasCR = true
            } else {
              lastCharWasCR = false
              lineBuffer.append(ch)
            }
          } else {
            if (ch == '\n') {
              emit(lineBuffer.toString)
              lineBuffer.clear()
            } else if (ch == '\r') {
              emit(lineBuffer.toString)
              lineBuffer.clear()
              lastCharWasCR = true
            } else {
              lineBuffer.append(ch)
            }
          }
        }
      }
      // Emit the last line if there's any content left and not ending with CR
      if (!lastCharWasCR && lineBuffer.nonEmpty) {
        emit(lineBuffer.toString)
      }
    }
  }

  /** Creates a flow using the given builder block that emits values through the FlowCollector. The
    * builder block is invoked when the flow is collected.
    *
    * Example:
    * {{{
    * // Creating a flow with a custom builder block
    * val flow = Flow.flow[Int] {
    *   // Calculate and emit values dynamically
    *   for (i <- 1 to 5) {
    *     if (i % 2 == 0) {
    *       Flow.emit(i * 10)
    *     }
    *   }
    * }
    *
    * val result = scala.collection.mutable.ArrayBuffer[Int]()
    * flow.collect { value =>
    *   result += value
    * }
    * // result contains: 20, 40
    * }}}
    *
    * @param builder
    *   The builder block that can emit values using the provided FlowCollector
    * @tparam A
    *   The type of values emitted by the flow
    * @return
    *   A flow that emits values from the builder block
    */
  def flow[A](builder: Flow.FlowCollector[A] ?=> Unit): Flow[A] = new Flow[A] {
    override def collect(collector: Flow.FlowCollector[A]): Unit = {
      given Flow.FlowCollector[A] = collector
      builder
    }
  }

  /** Emits a value to the current Flow collector. Can only be used within a flow builder block or
    * in context where a FlowCollector is available.
    *
    * Example:
    * {{{
    * // Using emit within a flow builder
    * val flow = Flow.flow[Int] {
    *   Flow.emit(1)
    *
    *   // Conditional emission
    *   val shouldEmit = true
    *   if (shouldEmit) {
    *     Flow.emit(2)
    *   }
    *
    *   // Emitting from a calculation
    *   val calculated = 3 + 4
    *   Flow.emit(calculated)
    * }
    *
    * val result = scala.collection.mutable.ArrayBuffer[Int]()
    * flow.collect { value =>
    *   result += value
    * }
    * // result contains: 1, 2, 7
    * }}}
    *
    * @param value
    *   The value to emit
    * @param collector
    *   The implicit collector to emit values to
    * @tparam A
    *   The type of the value
    */
  def emit[A](value: A)(using collector: Flow.FlowCollector[A]): Unit = {
    collector.emit(value)
  }

  /** Creates a flow by successively applying a function to a seed value to generate elements and a
    * new state.
    *
    * Example:
    * {{{
    * // Creating a flow via unfold
    * val fibonacciFlow = Flow.unfold((0, 1)) { case (a, b) =>
    *   if (a > 50) None
    *   else Some((a, (b, a + b)))
    * }
    *
    * val result = scala.collection.mutable.ArrayBuffer[Int]()
    * fibonacciFlow.collect { value =>
    *   actualResult += value
    * }
    *
    * // result contains: 0, 1, 1, 2, 3, 5, 8, 13, 21, 34
    * }}}
    *
    * @param seed
    *   the initial state used to generate the first element
    * @param step
    *   a function that takes the current state and returns an `Option` containing a tuple of the
    *   next element and the new state, or `None` to terminate the flow
    * @return
    *   a flow containing the sequence of elements generated
    */
  def unfold[S, A](seed: S)(step: S => Option[(A, S)]): Flow[A] = flow {
    var next = step(seed)
    while (next.isDefined) {
      Flow.emit(next.get._1)
      next = step(next.get._2)
    }
  }

  /** Creates a flow that reads data from an InputStream and emits it as byte arrays (chunks). The
    * flow will continue reading until the end of the stream is reached (when read returns -1).
    *
    * This method does NOT automatically close the InputStream. The caller is responsible for
    * managing the stream lifecycle using try-finally or resource management patterns.
    *
    * Example:
    * {{{
    * import java.io.FileInputStream
    * import scala.util.Using
    *
    * // Reading from a file with resource management
    * Using(new FileInputStream("data.txt")) { inputStream =>
    *   val chunks = scala.collection.mutable.ArrayBuffer[Array[Byte]]()
    *   Flow.fromInputStream(inputStream, bufferSize = 1024).collect { chunk =>
    *     chunks += chunk
    *   }
    *   // Process chunks...
    * }
    *
    * // Reading with custom buffer size
    * val inputStream = new java.io.ByteArrayInputStream("Hello, World!".getBytes())
    * val result = scala.collection.mutable.ArrayBuffer[Array[Byte]]()
    * Flow.fromInputStream(inputStream, bufferSize = 5).collect { chunk =>
    *   result += chunk
    * }
    * // result contains chunks of up to 5 bytes each
    * }}}
    *
    * @param inputStream
    *   The InputStream to read from
    * @param bufferSize
    *   The size of the buffer used for reading chunks (default: 8192 bytes)
    * @return
    *   A flow that emits byte arrays read from the stream
    * @throws IllegalArgumentException
    *   if bufferSize is less than or equal to 0
    * @throws java.io.IOException
    *   if an I/O error occurs during reading
    */
  def fromInputStream(
      inputStream: java.io.InputStream,
      bufferSize: Int = 8192
  ): Flow[Array[Byte]] = {
    if (bufferSize <= 0) {
      throw new IllegalArgumentException(s"bufferSize must be greater than 0, but was $bufferSize")
    }

    flow {
      val buffer    = new Array[Byte](bufferSize)
      var bytesRead = inputStream.read(buffer)

      while (bytesRead != -1) {
        if (bytesRead > 0) {
          val chunk = new Array[Byte](bytesRead)
          System.arraycopy(buffer, 0, chunk, 0, bytesRead)
          emit(chunk)
        }
        bytesRead = inputStream.read(buffer)
      }
    }
  }

  /** Creates a flow that reads data from a file at the given path and emits it as byte arrays
    * (chunks). The flow will continue reading until the end of the file is reached.
    *
    * This method automatically manages the file's InputStream lifecycle - the stream is opened
    * when collection starts and is automatically closed when collection completes (either
    * successfully or due to an exception).
    *
    * Example:
    * {{{
    * import java.nio.file.Paths
    * import scala.collection.mutable.ArrayBuffer
    *
    * // Reading a text file
    * val path = Paths.get("data.txt")
    * val content = Flow.fromFile(path)
    *   .asUtf8String()
    *   .fold("")(_ + _)
    *
    * // Reading and processing lines
    * val lines = ArrayBuffer[String]()
    * Flow.fromFile(path)
    *   .linesInUtf8()
    *   .collect { line =>
    *     lines += line
    *   }
    *
    * // Copying a file
    * import java.nio.file.Files
    * import scala.util.Using
    *
    * val destPath = Paths.get("copy.txt")
    * Using(Files.newOutputStream(destPath)) { outputStream =>
    *   Flow.fromFile(path).toOutputStream(outputStream)
    * }
    *
    * // Processing with custom buffer size
    * Flow.fromFile(path, bufferSize = 4096)
    *   .asUtf8String()
    *   .collect { chunk => println(chunk) }
    * }}}
    *
    * @param path
    *   The path to the file to read
    * @param bufferSize
    *   The size of the buffer used for reading chunks (default: 8192 bytes)
    * @return
    *   A flow that emits byte arrays read from the file
    * @throws IllegalArgumentException
    *   if bufferSize is less than or equal to 0
    * @throws java.io.IOException
    *   if the file does not exist, is a directory, cannot be read, or if an I/O error occurs
    *   during reading. The exception message includes the file path for context.
    */
  def fromFile(
      path: java.nio.file.Path,
      bufferSize: Int = 8192
  ): Flow[Array[Byte]] = {
    if (bufferSize <= 0) {
      throw new IllegalArgumentException(s"bufferSize must be greater than 0, but was $bufferSize")
    }

    flow {
      var inputStream: java.io.InputStream = null
      try {
        inputStream = java.nio.file.Files.newInputStream(path)
        fromInputStream(inputStream, bufferSize).collect { chunk =>
          emit(chunk)
        }
      } catch {
        case e: java.io.IOException =>
          throw new java.io.IOException(s"Failed to read file: $path", e)
      } finally {
        if (inputStream != null) {
          try {
            inputStream.close()
          } catch {
            case _: java.io.IOException => // Ignore close exceptions
          }
        }
      }
    }
  }

  /** Creates a flow that emits the given varargs elements.
    *
    * Example:
    * {{{
    * // Creating a flow from varargs
    * val flow = Flow(1, 2, 3, 4, 5)
    *
    * val result = scala.collection.mutable.ArrayBuffer[Int]()
    * flow.collect { value =>
    *   result += value
    * }
    * // result contains: 1, 2, 3, 4, 5
    * }}}
    *
    * @param elements
    *   The elements to emit
    * @tparam A
    *   The type of elements
    * @return
    *   A flow that emits the given elements
    */
  def apply[A](elements: A*): Flow[A] = flow {
    elements.foreach(item => emit(item))
  }

}
