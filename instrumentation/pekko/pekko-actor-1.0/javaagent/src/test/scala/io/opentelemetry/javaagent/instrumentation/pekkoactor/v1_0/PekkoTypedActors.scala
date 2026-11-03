/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkoactor.v1_0

import org.apache.pekko.actor.typed.{
  ActorRef,
  ActorSystem,
  Behavior,
  Props,
  SpawnProtocol
}
import org.apache.pekko.actor.typed.scaladsl.AskPattern._
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.util.Timeout
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context

import scala.concurrent.Await
import scala.concurrent.duration._

object PekkoTypedActors {
  val tracer: Tracer = GlobalOpenTelemetry.getTracer("test")

  implicit val timeout: Timeout = 10.seconds

  val system: ActorSystem[SpawnProtocol.Command] =
    ActorSystem(SpawnProtocol(), "helloPekkoTyped")

  val greeter: ActorRef[TypedGreeter.Command] =
    spawn(TypedGreeter(), "typedGreeter")

  val delegator: ActorRef[TypedDelegator.Delegate] =
    spawn(TypedDelegator(), "typedDelegator")

  private def spawn[T](behavior: Behavior[T], name: String): ActorRef[T] = {
    implicit val actorSystem: ActorSystem[SpawnProtocol.Command] = system
    Await.result(
      system.ask[ActorRef[T]](replyTo =>
        SpawnProtocol.Spawn(behavior, name, Props.empty, replyTo)
      ),
      10.seconds
    )
  }

  def tracedChild(opName: String): Unit = {
    tracer.spanBuilder(opName).startSpan().end()
  }
}

class PekkoTypedActors {

  import PekkoTypedActors._
  import TypedGreeter._

  def basicTell(): Unit = {
    val parentSpan = tracer.spanBuilder("parent").startSpan()
    val parentScope =
      Context.current().`with`(parentSpan).makeCurrent()
    try {
      greeter ! Greet("Pekko")
    } finally {
      parentScope.close()
      parentSpan.end()
    }
  }

  def basicAsk(): Unit = {
    implicit val actorSystem: ActorSystem[SpawnProtocol.Command] = system
    val parentSpan = tracer.spanBuilder("parent").startSpan()
    val parentScope =
      Context.current().`with`(parentSpan).makeCurrent()
    try {
      Await.result(
        greeter.ask[Greeted](replyTo => GreetAndReply("Pekko", replyTo)),
        10.seconds
      )
    } finally {
      parentScope.close()
      parentSpan.end()
    }
  }

  def spawnChildAndTell(): Unit = {
    val parentSpan = tracer.spanBuilder("parent").startSpan()
    val parentScope =
      Context.current().`with`(parentSpan).makeCurrent()
    try {
      delegator ! TypedDelegator.Delegate("Pekko")
    } finally {
      parentScope.close()
      parentSpan.end()
    }
  }
}

object TypedGreeter {
  sealed trait Command

  case class Greet(who: String) extends Command

  case class GreetAndReply(who: String, replyTo: ActorRef[Greeted])
      extends Command

  case class Greeted(greeting: String)

  def apply(): Behavior[Command] =
    Behaviors.receiveMessage {
      case Greet(who) =>
        PekkoTypedActors.tracedChild(s"Hello, $who")
        Behaviors.same
      case GreetAndReply(who, replyTo) =>
        val greeting = s"Hello, $who"
        PekkoTypedActors.tracedChild(greeting)
        replyTo ! Greeted(greeting)
        Behaviors.same
    }
}

object TypedDelegator {
  case class Delegate(who: String)

  // spawns a child for every message and hands the message on to it, the child stops once it
  // has greeted
  def apply(): Behavior[Delegate] =
    Behaviors.receive { (context, message) =>
      val child = context.spawnAnonymous(
        Behaviors.receiveMessage[TypedGreeter.Command] {
          case TypedGreeter.Greet(who) =>
            PekkoTypedActors.tracedChild(s"Hi, $who")
            Behaviors.stopped
          case _ => Behaviors.unhandled
        }
      )
      child ! TypedGreeter.Greet(message.who)
      Behaviors.same
    }
}
