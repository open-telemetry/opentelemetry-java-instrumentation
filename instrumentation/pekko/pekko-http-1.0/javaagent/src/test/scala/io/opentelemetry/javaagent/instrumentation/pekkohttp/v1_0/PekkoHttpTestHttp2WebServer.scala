/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0

import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.Http.ServerBinding
import org.apache.pekko.http.scaladsl.model.HttpMethods.GET
import org.apache.pekko.http.scaladsl.model.{
  HttpRequest,
  HttpResponse,
  StatusCodes
}

import scala.concurrent.{Await, ExecutionContextExecutor, Future}

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

  // a client that negotiates an h2c upgrade sends an OPTIONS * request to upgrade with, the
  // handler that the other tests share only answers GET
  private val handler: HttpRequest => Future[HttpResponse] = {
    val getHandler = PekkoHttpTestAsyncHandler.asyncHandler
    request =>
      if (request.method == GET) {
        getHandler(request)
      } else {
        Future.successful(HttpResponse(status = StatusCodes.NotFound))
      }
  }

  private var binding: ServerBinding = _

  def start(port: Int): Unit = synchronized {
    if (binding == null) {
      import scala.concurrent.duration._
      binding = Await.result(
        Http()
          .newServerAt("localhost", port)
          .bind(handler),
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
