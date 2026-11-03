/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0

import com.typesafe.config.ConfigFactory
import io.opentelemetry.context.Context
import io.opentelemetry.instrumentation.testing.junit.http.{
  AbstractHttpServerTest,
  ServerEndpoint
}
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.Http.ServerBinding
import org.apache.pekko.http.scaladsl.model.HttpMethods.GET
import org.apache.pekko.http.scaladsl.model.{
  HttpRequest,
  HttpResponse,
  StatusCodes
}

import java.util.concurrent.{Executors, ThreadLocalRandom, TimeUnit}
import scala.concurrent.{Await, ExecutionContextExecutor, Future, Promise}

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

  val SEPARATE_EXECUTOR =
    new ServerEndpoint("SEPARATE_EXECUTOR", "separate-executor", 200, "done")

  // completes the responses of the SEPARATE_EXECUTOR endpoint on threads that pekko does not own
  private val completionExecutor = Executors.newScheduledThreadPool(4)

  // the response is completed later, on a thread of the application's own executor, with the
  // context of the request current, like application code that hands the request off to its own
  // thread pool does
  private def separateExecutorHandler(): Future[HttpResponse] = {
    val context = Context.current()
    val promise = Promise[HttpResponse]()
    completionExecutor.schedule(
      new Runnable {
        override def run(): Unit = {
          val scope = context.makeCurrent()
          try {
            promise.success(
              AbstractHttpServerTest.controller(
                SEPARATE_EXECUTOR,
                () =>
                  HttpResponse(status = SEPARATE_EXECUTOR.getStatus)
                    .withEntity(SEPARATE_EXECUTOR.getBody)
              )
            )
          } finally {
            scope.close()
          }
        }
      },
      ThreadLocalRandom.current().nextLong(20),
      TimeUnit.MILLISECONDS
    )
    promise.future
  }

  // a client that negotiates an h2c upgrade sends an OPTIONS * request to upgrade with, the
  // handler that the other tests share only answers GET
  private val handler: HttpRequest => Future[HttpResponse] = {
    val getHandler = PekkoHttpTestAsyncHandler.asyncHandler
    request =>
      if (
        request.method == GET && request.uri.path.toString == SEPARATE_EXECUTOR.getPath
      ) {
        separateExecutorHandler()
      } else if (request.method == GET) {
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
      completionExecutor.shutdown()
      binding = null
    }
  }
}
