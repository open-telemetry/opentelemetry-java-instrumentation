/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0

import io.opentelemetry.api.trace.Span
import org.apache.pekko.actor.{Actor, ActorSystem, Props}
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.Http.ServerBinding
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.pattern.ask
import org.apache.pekko.util.Timeout

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration.DurationInt

object PekkoHttpTestWebServerWithActor {
  implicit val system: ActorSystem = ActorSystem("http-server-with-actor")
  // needed for the future flatMap/onComplete in the end
  implicit val executionContext: ExecutionContext = system.dispatcher

  private case object TestMessage
  private class SpanTestActor extends Actor {
    def receive = { case TestMessage =>
      sender() ! spanSummary(Span.current())
    }
  }

  val spanTestActor = system.actorOf(Props[SpanTestActor]())

  var route = get {
    path("test") {
      complete {
        val otelSummary = spanSummary(Span.current())
        spanTestActor.ask(TestMessage)(Timeout(5.seconds)).mapTo[String].map {
          actorSummary =>
            s"Route=$otelSummary\nActor=$actorSummary"
        }
      }
    }
  }

  private var binding: ServerBinding = null

  def start(port: Int): Unit = synchronized {
    if (null == binding) {
      binding =
        Await.result(Http().bindAndHandle(route, "localhost", port), 10.seconds)
    }
  }

  def stop(): Unit = synchronized {
    if (null != binding) {
      binding.unbind()
      system.terminate()
      binding = null
    }
  }

  def spanSummary(span: Span): String = {
    val spanId = span.getSpanContext().getSpanId()
    val traceId = span.getSpanContext().getTraceId()
    s"Span(traceId=$traceId, spanId=$spanId)"
  }
}
