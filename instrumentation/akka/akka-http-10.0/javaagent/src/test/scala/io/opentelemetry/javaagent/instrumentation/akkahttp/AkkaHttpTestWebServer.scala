/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.ExceptionHandler
import akka.stream.ActorMaterializer
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint._

import scala.concurrent.Await

// FIXME: This doesn't work because we don't support bindAndHandle.
object AkkaHttpTestWebServer {
  implicit val system = ActorSystem("my-system")
  implicit val materializer = ActorMaterializer()
  // needed for the future flatMap/onComplete in the end
  implicit val executionContext = system.dispatcher

  val exceptionHandler = ExceptionHandler { case ex: Exception =>
    complete(
      HttpResponse(status = EXCEPTION.getStatus).withEntity(ex.getMessage)
    )
  }

  val route = { // handleExceptions(exceptionHandler) {
    path(SUCCESS.rawPath) {
      complete(
        HttpResponse(status = SUCCESS.getStatus).withEntity(SUCCESS.getBody)
      )
    } ~ path(QUERY_PARAM.rawPath) {
      complete(
        HttpResponse(status = QUERY_PARAM.getStatus).withEntity(SUCCESS.getBody)
      )
    } ~ path(REDIRECT.rawPath) {
      redirect(Uri(REDIRECT.getBody), StatusCodes.Found)
    } ~ path(ERROR.rawPath) {
      complete(HttpResponse(status = ERROR.getStatus).withEntity(ERROR.getBody))
    } ~ path(EXCEPTION.rawPath) {
      failWith(new Exception(EXCEPTION.getBody))
    }
  }

  private var binding: ServerBinding = null

  def start(port: Int): Unit = synchronized {
    if (null == binding) {
      import scala.concurrent.duration._
      binding =
        Await.result(Http().bindAndHandle(route, "localhost", port), 10 seconds)
    }
  }

  def stop(): Unit = synchronized {
    if (null != binding) {
      binding.unbind()
      system.terminate()
      binding = null
    }
  }
}
