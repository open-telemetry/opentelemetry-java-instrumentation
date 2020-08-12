/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import io.opentelemetry.OpenTelemetry
import io.opentelemetry.trace.Tracer

import scala.concurrent.duration._

// ! == send-message
object AkkaActors {
  val TRACER: Tracer =
    OpenTelemetry.getTracerProvider.get("io.opentelemetry.auto")

  val system: ActorSystem = ActorSystem("helloAkka")

  val printer: ActorRef = system.actorOf(Receiver.props, "receiverActor")

  val howdyGreeter: ActorRef =
    system.actorOf(Greeter.props("Howdy", printer), "howdyGreeter")

  val forwarder: ActorRef =
    system.actorOf(Forwarder.props(printer), "forwarderActor")
  val helloGreeter: ActorRef =
    system.actorOf(Greeter.props("Hello", forwarder), "helloGreeter")

  def tracedChild(opName: String): Unit = {
    TRACER.spanBuilder(opName).startSpan().end()
  }
}

class AkkaActors {

  import AkkaActors._
  import Greeter._

  implicit val timeout: Timeout = 5.minutes

  def basicTell(): Unit = {
    val parentSpan = TRACER.spanBuilder("parent").startSpan()
    val parentScope = TRACER.withSpan(parentSpan)
    try {
      howdyGreeter ! WhoToGreet("Akka")
      howdyGreeter ! Greet
    } finally {
      parentSpan.end()
      parentScope.close()
    }
  }

  def basicAsk(): Unit = {
    val parentSpan = TRACER.spanBuilder("parent").startSpan()
    val parentScope = TRACER.withSpan(parentSpan)
    try {
      howdyGreeter ! WhoToGreet("Akka")
      howdyGreeter ? Greet
    } finally {
      parentSpan.end()
      parentScope.close()
    }
  }

  def basicForward(): Unit = {
    val parentSpan = TRACER.spanBuilder("parent").startSpan()
    val parentScope = TRACER.withSpan(parentSpan)
    try {
      helloGreeter ! WhoToGreet("Akka")
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
  def props: Props = Props[Receiver]

  final case class Greeting(greeting: String)

}

class Receiver extends Actor with ActorLogging {

  import Receiver._

  def receive = {
    case Greeting(greeting) => {
      AkkaActors.tracedChild(greeting)
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
