/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkoremote.v1_0

import com.typesafe.config.ConfigFactory
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.javaagent.instrumentation.pekkoremote.v1_0.Forwarder.Forward
import io.opentelemetry.javaagent.instrumentation.pekkoremote.v1_0.PekkoRemoteActors.{
  portB,
  systemBName,
  tracedChild
}
import io.opentelemetry.javaagent.testing.common.Java8BytecodeBridge
import org.apache.pekko.actor.{
  Actor,
  ActorLogging,
  ActorRef,
  ActorSystem,
  Address,
  Props
}
import org.apache.pekko.pattern.ask
import org.apache.pekko.util.Timeout

import scala.concurrent.duration.DurationInt
import scala.util.Success

object PekkoRemoteActors {

  val tracer: Tracer = GlobalOpenTelemetry.getTracer("test")

  val systemA: ActorSystem =
    ActorSystem("helloPekkoA", remoteConfig(port = 17355))

  val systemBName = "helloPekkoB"
  val portB = 17356
  val systemB: ActorSystem =
    ActorSystem(systemBName, remoteConfig(port = portB))

  val forwarderOnSystemA: ActorRef =
    systemA.actorOf(Forwarder.props(), "a")

  val receiverOnSystemB: ActorRef =
    systemB.actorOf(Receiver.props(), "b")

  def tracedChild(opName: String)(op: => Unit): Unit = {
    val span = tracer.spanBuilder(opName).startSpan()
    try {
      op
    } finally span.end()
  }

  private def remoteConfig(port: Int) = {
    ConfigFactory.parseString(s"""
         |pekko {
         |  actor {
         |    provider = remote
         |  }
         |  remote {
         |    artery {
         |      transport = tcp
         |      canonical.hostname = "127.0.0.1"
         |      canonical.port = $port
         |    }
         |  }
         |}
         |""".stripMargin)

  }

}

class PekkoRemoteActors {

  import PekkoRemoteActors._

  implicit val timeout: Timeout = 15.seconds

  def basicForward(): Unit = {
    val parentSpan = tracer.spanBuilder("parent").startSpan()
    val parentScope =
      Java8BytecodeBridge.currentContext().`with`(parentSpan).makeCurrent()
    try {
      forwarderOnSystemA ! Forward(receiverOnSystemB, "Pekko")
    } finally {
      parentSpan.end()
      parentScope.close()
    }
  }
}

object Forwarder {
  case class Forward[T](ref: ActorRef, msg: T)

  def props(): Props =
    Props(new Forwarder())
}

class Forwarder extends Actor with ActorLogging {

  private implicit val timeout: Timeout = Timeout(5.seconds)

  def receive = { case Forward(ref, msg) =>
    val remoteSelectionPath = ref.path.toStringWithAddress(
      Address.apply("pekko", systemBName, "127.0.0.1", portB)
    )

    val selection = context.actorSelection(remoteSelectionPath)
    import context.dispatcher

    (selection ? s"Howdy, $msg").onComplete {
      case Success(answer: String) => tracedChild(answer)()
      case _                       =>
    }
  }

}

object Receiver {
  def props(): Props =
    Props(new Receiver())
}

class Receiver extends Actor with ActorLogging {
  def receive = { case msg: String =>
    tracedChild(msg) {
      sender() ! "Nice to meet you!"
    }
  }
}
