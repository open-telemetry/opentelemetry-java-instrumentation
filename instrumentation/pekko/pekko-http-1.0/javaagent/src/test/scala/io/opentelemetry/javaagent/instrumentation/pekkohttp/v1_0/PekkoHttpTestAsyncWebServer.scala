/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.Http.ServerBinding

import scala.concurrent.{Await, ExecutionContextExecutor}

object PekkoHttpTestAsyncWebServer {
  implicit val system: ActorSystem = ActorSystem("my-system")
  // needed for the future flatMap/onComplete in the end
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher

  private var binding: ServerBinding = _

  def start(port: Int): Unit = synchronized {
    if (binding == null) {
      import scala.concurrent.duration._
      binding = Await.result(
        Http().bindAndHandleAsync(
          PekkoHttpTestAsyncHandler.asyncHandler,
          "localhost",
          port
        ),
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
