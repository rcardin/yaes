package in.rcard.yaes

trait Flow[A] {
  def collect(collector: Flow.FlowCollector[A]): Unit
}

object Flow {

  extension [A](seq: Seq[A])
    def asFlow(): Flow[A] = flow {
      seq.foreach(item => emit(item))
    }

  def flow[A](builder: Flow.FlowCollector[A] ?=> Unit): Flow[A] = new Flow[A] {
    override def collect(collector: Flow.FlowCollector[A]): Unit = {
      given Flow.FlowCollector[A] = collector
      builder
    }
  }

  def emit[A](value: A)(using collector: Flow.FlowCollector[A]): Unit = {
    collector.emit(value)
  }

  trait FlowCollector[A] {
    def emit(value: A): Unit
  }

}
