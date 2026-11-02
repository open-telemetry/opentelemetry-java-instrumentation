/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkoactor.v1_0

import com.typesafe.config.ConfigFactory
import org.apache.pekko.Done
import org.apache.pekko.actor.{
  Actor,
  ActorLogging,
  ActorRef,
  ActorSystem,
  Props,
  Stash
}
import org.apache.pekko.pattern.ask
import org.apache.pekko.util.Timeout
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context

import scala.concurrent.Await
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

  // the guardians of an actor system run on its internal dispatcher, with a single thread for it
  // an actor that runs on it can create a top level actor that won't start before the actor that
  // created it has handled its current message
  val startupSystem: ActorSystem = ActorSystem(
    "pekkoStartup",
    ConfigFactory
      .parseString(
        """pekko.actor.internal-dispatcher {
          |  executor = "thread-pool-executor"
          |  thread-pool-executor.fixed-pool-size = 1
          |}""".stripMargin
      )
      .withFallback(ConfigFactory.load())
  )

  val spawner: ActorRef = startupSystem.actorOf(
    Spawner.props(printer).withDispatcher("pekko.actor.internal-dispatcher"),
    "spawner"
  )

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
      Context.current().`with`(parentSpan).makeCurrent()
    try {
      howdyGreeter ! WhoToGreet("Pekko")
      howdyGreeter ! Greet
    } finally {
      parentScope.close()
      parentSpan.end()
    }
  }

  def basicAsk(): Unit = {
    val parentSpan = tracer.spanBuilder("parent").startSpan()
    val parentScope =
      Context.current().`with`(parentSpan).makeCurrent()
    try {
      howdyGreeter ! WhoToGreet("Pekko")
      howdyGreeter ? Greet
    } finally {
      parentScope.close()
      parentSpan.end()
    }
  }

  def stashAndUnstash(): Unit = {
    // a fresh actor per call starts out locked
    val stasher = system.actorOf(Stasher.props(printer))
    val parentSpan = tracer.spanBuilder("parent").startSpan()
    val parentScope =
      Context.current().`with`(parentSpan).makeCurrent()
    try {
      stasher ! WhoToGreet("Pekko")
      stasher ! Greet
    } finally {
      parentScope.close()
      parentSpan.end()
    }
    val unlockSpan = tracer.spanBuilder("unlock").startSpan()
    val unlockScope =
      Context.current().`with`(unlockSpan).makeCurrent()
    try {
      stasher ! Stasher.Unlock
    } finally {
      unlockScope.close()
      unlockSpan.end()
    }
  }

  def tellBeforeStarted(): Unit = {
    val createSpan = tracer.spanBuilder("create").startSpan()
    val createScope =
      Context.current().`with`(createSpan).makeCurrent()
    try {
      Await.result(spawner ? Spawner.Spawn, 10.seconds)
    } finally {
      createScope.close()
      createSpan.end()
    }
  }

  def basicForward(): Unit = {
    val parentSpan = tracer.spanBuilder("parent").startSpan()
    val parentScope =
      Context.current().`with`(parentSpan).makeCurrent()
    try {
      helloGreeter ! WhoToGreet("Pekko")
      helloGreeter ? Greet
    } finally {
      parentScope.close()
      parentSpan.end()
    }
  }
}

object Greeter {
  def props(message: String, receiverActor: ActorRef): Props =
    Props(new Greeter(message, receiverActor))

  case class WhoToGreet(who: String)

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

  case class Greeting(greeting: String)

}

class Receiver extends Actor with ActorLogging {

  import Receiver._

  def receive = {
    case Greeting(greeting) => {
      PekkoActors.tracedChild(greeting)
    }

  }
}

object Stasher {
  def props(receiverActor: ActorRef): Props =
    Props(new Stasher(receiverActor))

  case object Unlock
}

// stashes messages until it is unlocked, then greets like a Greeter
class Stasher(receiverActor: ActorRef) extends Actor with Stash {

  import Greeter._
  import Receiver._
  import Stasher._

  var greeting = ""

  def receive = {
    case Unlock =>
      unstashAll()
      context.become(unlocked)
    case _ =>
      stash()
  }

  def unlocked: Receive = {
    case WhoToGreet(who) =>
      greeting = s"Hello, $who"
    case Greet =>
      receiverActor ! Greeting(greeting)
  }
}

object Spawner {
  def props(receiverActor: ActorRef): Props =
    Props(new Spawner(receiverActor))

  case object Spawn
}

// creates a top level greeter and greets with it before the greeter has started
class Spawner(receiverActor: ActorRef) extends Actor {

  import Greeter._
  import Spawner._

  def receive = { case Spawn =>
    // the guardian starts the greeter on the thread that runs this actor, so the greeter can't
    // start before this message has been handled and the messages are queued until it starts
    val greeter = context.system.actorOf(Greeter.props("Hi", receiverActor))
    val parentSpan =
      PekkoActors.tracer.spanBuilder("parent").setNoParent().startSpan()
    val parentScope =
      Context.current().`with`(parentSpan).makeCurrent()
    try {
      greeter ! WhoToGreet("Pekko")
      greeter ! Greet
    } finally {
      parentScope.close()
      parentSpan.end()
    }
    // the guardian starts the greeter in the context of this message, messages sent without a
    // context must not end up in it, a new thread is used as it has no context at all
    val rootSender = new Thread(() => {
      greeter ! WhoToGreet("Root")
      greeter ! Greet
    })
    rootSender.start()
    rootSender.join()
    sender() ! Done
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
