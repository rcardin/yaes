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
     * A flow that pairs each element of the original flow with its index beginning at 0
     */
    def zipWithIndex(): Flow[(A, Long)] = Flow.flow {
      var index: Long = 0L
      originalFlow.collect { a =>
        Flow.emit((a, index))
        index += 1
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
    def asString(charset: java.nio.charset.Charset): Flow[String] = new Flow[String] {
      override def collect(collector: Flow.FlowCollector[String]): Unit = {
        val decoder = charset
          .newDecoder()
          .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
          .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
        
        // Buffer to accumulate incomplete byte sequences
        var incompleteBytes = Array.empty[Byte]

        byteFlow.collect { bytes =>
          // Prepend any incomplete bytes from the previous chunk
          val fullBytes = incompleteBytes ++ bytes
          val inputBuffer = java.nio.ByteBuffer.wrap(fullBytes)
          // Allocate enough space for worst case
          val outputBuffer = java.nio.CharBuffer.allocate(fullBytes.length * 3)
          
          // Decode with endOfInput=false to handle incomplete sequences
          val result = decoder.decode(inputBuffer, outputBuffer, false)
          
          // Check if there are remaining bytes (incomplete character sequence)
          if (inputBuffer.hasRemaining) {
            // Save incomplete bytes for next chunk
            val remaining = new Array[Byte](inputBuffer.remaining())
            inputBuffer.get(remaining)
            incompleteBytes = remaining
          } else {
            incompleteBytes = Array.empty[Byte]
          }
          
          outputBuffer.flip()
          if (outputBuffer.hasRemaining) {
            collector.emit(outputBuffer.toString)
          }
        }
        
        // Process any remaining incomplete bytes at the end
        if (incompleteBytes.nonEmpty) {
          val inputBuffer = java.nio.ByteBuffer.wrap(incompleteBytes)
          val outputBuffer = java.nio.CharBuffer.allocate(incompleteBytes.length * 3)
          decoder.decode(inputBuffer, outputBuffer, true)
          decoder.flush(outputBuffer)
          outputBuffer.flip()
          if (outputBuffer.hasRemaining) {
            collector.emit(outputBuffer.toString)
          }
        } else {
          // Just flush in case
          val finalBuffer = java.nio.CharBuffer.allocate(10)
          decoder.decode(java.nio.ByteBuffer.allocate(0), finalBuffer, true)
          decoder.flush(finalBuffer)
          finalBuffer.flip()
          if (finalBuffer.hasRemaining) {
            collector.emit(finalBuffer.toString)
          }
        }
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

  /**
   * Creates a flow by successively applying a function to a seed value to generate elements and a new state.
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
   * @param seed the initial state used to generate the first element
   * @param step a function that takes the current state and returns an `Option` containing a tuple of 
   *             the next element and the new state, or `None` to terminate the flow
   * @return a flow containing the sequence of elements generated
   */
  def unfold[S, A](seed: S)(step: S => Option[(A, S)]): Flow[A] = flow {
    var next = step(seed)
    while (next.isDefined) {
      Flow.emit(next.get._1)
      next = step(next.get._2)
    }
  }

  /** Creates a flow that reads data from an InputStream and emits it as byte arrays (chunks).
    * The flow will continue reading until the end of the stream is reached (when read returns -1).
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
      val buffer   = new Array[Byte](bufferSize)
      var bytesRead = inputStream.read(buffer)
      
      while (bytesRead != -1) {
        if (bytesRead > 0) {
          emit(buffer.take(bytesRead))
        }
        bytesRead = inputStream.read(buffer)
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
