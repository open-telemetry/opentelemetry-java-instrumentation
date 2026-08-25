/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0

import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.Http.ServerBinding

import scala.concurrent.{Await, ExecutionContextExecutor}

object PekkoHttpTestHttp2WebServer {
  // `preview.enable-http2` is the setting used by pekko-http 1.0, it was replaced with
  // `enable-http2` in 1.3
  implicit val system: ActorSystem = ActorSystem(
    "http2-system",
    ConfigFactory
      .parseString("""
        |pekko.http.server.preview.enable-http2 = on
        |pekko.http.server.enable-http2 = on
        |""".stripMargin)
      .withFallback(ConfigFactory.load())
  )
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher

  private var binding: ServerBinding = _

  def start(port: Int): Unit = synchronized {
    if (binding == null) {
      import scala.concurrent.duration._
      binding = Await.result(
        Http()
          .newServerAt("localhost", port)
          .bind(PekkoHttpTestAsyncHandler.asyncHandler),
        10.seconds
      )
    }
  }

  def stop(): Unit = synchronized {
    if (binding != null) {
      binding.unbind()
      system.terminate()
      binding = null
    }
  }
}
