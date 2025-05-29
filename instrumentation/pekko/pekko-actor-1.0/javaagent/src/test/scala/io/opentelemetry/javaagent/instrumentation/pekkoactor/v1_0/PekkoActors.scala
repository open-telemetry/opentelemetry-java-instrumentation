/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkoactor.v1_0

import org.apache.pekko.actor.{
  Actor,
  ActorLogging,
  ActorRef,
  ActorSystem,
  Props
}
import org.apache.pekko.pattern.ask
import org.apache.pekko.util.Timeout
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.javaagent.testing.common.Java8BytecodeBridge

import scala.concurrent.duration._

// ! == send-message
object PekkoActors {
  val tracer: Tracer = GlobalOpenTelemetry.getTracer("test")

  val system: ActorSystem = ActorSystem("helloPekko")

  val printer: ActorRef = system.actorOf(Receiver.props, "receiverActor")

  val howdyGreeter: ActorRef =
    system.actorOf(Greeter.props("Howdy", printer), "howdyGreeter")

  val forwarder: ActorRef =
    system.actorOf(Forwarder.props(printer), "forwarderActor")
  val helloGreeter: ActorRef =
    system.actorOf(Greeter.props("Hello", forwarder), "helloGreeter")

  def tracedChild(opName: String): Unit = {
    tracer.spanBuilder(opName).startSpan().end()
  }
}

class PekkoActors {

  import PekkoActors._
  import Greeter._

  implicit val timeout: Timeout = 5.minutes

  def basicTell(): Unit = {
    val parentSpan = tracer.spanBuilder("parent").startSpan()
    val parentScope =
      Java8BytecodeBridge.currentContext().`with`(parentSpan).makeCurrent()
    try {
      howdyGreeter ! WhoToGreet("Pekko")
      howdyGreeter ! Greet
    } finally {
      parentSpan.end()
      parentScope.close()
    }
  }

  def basicAsk(): Unit = {
    val parentSpan = tracer.spanBuilder("parent").startSpan()
    val parentScope =
      Java8BytecodeBridge.currentContext().`with`(parentSpan).makeCurrent()
    try {
      howdyGreeter ! WhoToGreet("Pekko")
      howdyGreeter ? Greet
    } finally {
      parentSpan.end()
      parentScope.close()
    }
  }

  def basicForward(): Unit = {
    val parentSpan = tracer.spanBuilder("parent").startSpan()
    val parentScope =
      Java8BytecodeBridge.currentContext().`with`(parentSpan).makeCurrent()
    try {
      helloGreeter ! WhoToGreet("Pekko")
      helloGreeter ? Greet
    } finally {
      parentSpan.end()
      parentScope.close()
    }
  }
}

object Greeter {
  def props(message: String, receiverActor: ActorRef): Props =
    Props(new Greeter(message, receiverActor))

  final case class WhoToGreet(who: String)

  case object Greet

}

class Greeter(message: String, receiverActor: ActorRef) extends Actor {

  import Greeter._
  import Receiver._

  var greeting = ""

  def receive = {
    case WhoToGreet(who) =>
      greeting = s"$message, $who"
    case Greet =>
      receiverActor ! Greeting(greeting)
  }
}

object Receiver {
  def props: Props = Props[Receiver]()

  final case class Greeting(greeting: String)

}

class Receiver extends Actor with ActorLogging {

  import Receiver._

  def receive = {
    case Greeting(greeting) => {
      PekkoActors.tracedChild(greeting)
    }

  }
}

object Forwarder {
  def props(receiverActor: ActorRef): Props =
    Props(new Forwarder(receiverActor))
}

class Forwarder(receiverActor: ActorRef) extends Actor with ActorLogging {
  def receive = {
    case msg => {
      receiverActor forward msg
    }
  }
}
