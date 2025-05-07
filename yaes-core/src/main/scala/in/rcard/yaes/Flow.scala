package in.rcard.yaes

trait Flow[A] {
  def collect(collector: Flow.FlowCollector[A]): Unit
}

object Flow {

  trait FlowCollector[A] {
    def emit(value: A): Unit
  }

  extension [A](seq: Seq[A])
    def asFlow(): Flow[A] = flow {
      seq.foreach(item => emit(item))
    }

  extension [A](originalFlow: Flow[A]) {
    def onStart(action: Flow.FlowCollector[A] ?=> Unit): Flow[A] = new Flow[A] {
      override def collect(collector: Flow.FlowCollector[A]): Unit = {
        given Flow.FlowCollector[A] = collector
        action
        originalFlow.collect(collector)
      }
    }

    def transform[B](transform: FlowCollector[B] ?=> A => Unit): Flow[B] = new Flow[B] {
      override def collect(collector: Flow.FlowCollector[B]): Unit = {
        given Flow.FlowCollector[B] = collector
        originalFlow.collect { value =>
          transform(value)
        }
      }
    }

    def onEach(action: A => Unit): Flow[A] = originalFlow.transform { value =>
      action(value)
      Flow.emit(value)
    }

    def map[B](transform: A => B): Flow[B] = originalFlow.transform { value =>
      Flow.emit(transform(value))
    }

    def filter(predicate: A => Boolean): Flow[A] = transform { value =>
      if (predicate(value)) {
        Flow.emit(value)
      }
    }
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

  def apply[A](elements: A*): Flow[A] = flow {
    elements.foreach(item => emit(item))
  }

}
