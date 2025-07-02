/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0

import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint._
import io.opentelemetry.instrumentation.testing.util.ThrowingSupplier
import io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.AbstractHttpServerInstrumentationTest.TIMEOUT
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.Http.ServerBinding
import org.apache.pekko.http.scaladsl.model.StatusCodes.Found
import org.apache.pekko.http.scaladsl.model.headers.`Timeout-Access`
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.pattern.after

import scala.concurrent.duration.{Duration, MILLISECONDS, SECONDS}
import scala.concurrent.{Await, ExecutionContext, Future}

object PekkoHttpTestWebServer {
  implicit val system: ActorSystem = ActorSystem("my-system")
  // needed for the future flatMap/onComplete in the end
  implicit val executionContext: ExecutionContext = system.dispatcher

  var route = get {
    concat(
      path(TIMEOUT.rawPath()) {
        headerValueByType[`Timeout-Access`]() { timeout =>
          timeout.timeoutAccess.updateTimeout(Duration(1, MILLISECONDS))
          complete {
            after(Duration(1, SECONDS)) {
              Future.successful("You'll never see this")
            }
          }
        }
      },
      path(SUCCESS.rawPath()) {
        complete(
          AbstractHttpServerTest.controller(SUCCESS, supplier(SUCCESS.getBody))
        )
      },
      path(INDEXED_CHILD.rawPath()) {
        parameterMap { map =>
          val supplier = new ThrowingSupplier[String, Exception] {
            def get(): String = {
              INDEXED_CHILD.collectSpanAttributes(new UrlParameterProvider {
                override def getParameter(name: String): String =
                  map.get(name).orNull
              })
              INDEXED_CHILD.getBody
            }
          }
          complete(AbstractHttpServerTest.controller(INDEXED_CHILD, supplier))
        }
      },
      path(QUERY_PARAM.rawPath()) {
        extractUri { uri =>
          complete(
            AbstractHttpServerTest
              .controller(QUERY_PARAM, supplier(uri.queryString().orNull))
          )
        }
      },
      path(REDIRECT.rawPath()) {
        redirect(
          AbstractHttpServerTest
            .controller(REDIRECT, supplier(REDIRECT.getBody)),
          Found
        )
      },
      path(ERROR.rawPath()) {
        complete(
          500 -> AbstractHttpServerTest
            .controller(ERROR, supplier(ERROR.getBody))
        )
      },
      path("path" / LongNumber / "param") { id =>
        complete(
          AbstractHttpServerTest.controller(PATH_PARAM, supplier(id.toString))
        )
      },
      path(
        "test1" / IntNumber / HexIntNumber / LongNumber / HexLongNumber /
          DoubleNumber / JavaUUID / Remaining
      ) { (_, _, _, _, _, _, _) =>
        complete(SUCCESS.getBody)
      },
      pathPrefix("test2") {
        concat(
          path("first") {
            complete(SUCCESS.getBody)
          },
          path("second") {
            complete(SUCCESS.getBody)
          }
        )
      }
    )
  }

  private var binding: ServerBinding = null

  def start(port: Int): Unit = synchronized {
    if (null == binding) {
      import scala.concurrent.duration._
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

  def supplier(string: String): ThrowingSupplier[String, Exception] = { () =>
    {
      string
    }
  }
}
