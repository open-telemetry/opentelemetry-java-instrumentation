import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import datadog.trace.api.Trace
import datadog.trace.instrumentation.api.AgentTracer.{activeScope, activeSpan}

import scala.concurrent.duration._

// ! == send-message
object AkkaActors {
  val system: ActorSystem = ActorSystem("helloAkka")

  val printer: ActorRef = system.actorOf(Receiver.props, "receiverActor")

  val howdyGreeter: ActorRef =
    system.actorOf(Greeter.props("Howdy", printer), "howdyGreeter")

  val forwarder: ActorRef = system.actorOf(Forwarder.props(printer), "forwarderActor")
  val helloGreeter: ActorRef = system.actorOf(Greeter.props("Hello", forwarder), "helloGreeter")

  @Trace
  def tracedChild(opName: String): Unit = {
    activeSpan().setSpanName(opName)
  }
}

class AkkaActors {

  import AkkaActors._
  import Greeter._

  implicit val timeout: Timeout = 5.minutes

  @Trace
  def basicTell(): Unit = {
    activeScope().setAsyncPropagation(true)
    howdyGreeter ! WhoToGreet("Akka")
    howdyGreeter ! Greet
  }

  @Trace
  def basicAsk(): Unit = {
    activeScope().setAsyncPropagation(true)
    howdyGreeter ! WhoToGreet("Akka")
    howdyGreeter ? Greet
  }

  @Trace
  def basicForward(): Unit = {
    activeScope().setAsyncPropagation(true)
    helloGreeter ! WhoToGreet("Akka")
    helloGreeter ? Greet
  }
}

object Greeter {
  def props(message: String, receiverActor: ActorRef): Props = Props(new Greeter(message, receiverActor))

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
  def props(receiverActor: ActorRef): Props = Props(new Forwarder(receiverActor))
}

class Forwarder(receiverActor: ActorRef) extends Actor with ActorLogging {
  def receive = {
    case msg => {
      receiverActor forward msg
    }
  }
}
