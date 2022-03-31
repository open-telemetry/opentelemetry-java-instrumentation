/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.finatra

import com.twitter.finagle.http.{Request, Response}
import com.twitter.finatra.http.Controller
import com.twitter.util.Future
import groovy.lang.Closure
import io.opentelemetry.instrumentation.test.base.HttpServerTest.controller
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint._

class FinatraController extends Controller {
  any(SUCCESS.getPath) { request: Request =>
    controller(
      SUCCESS,
      new Closure[Response](null) {
        override def call(): Response = {
          response.ok(SUCCESS.getBody)
        }
      }
    )
  }

  any(ERROR.getPath) { request: Request =>
    controller(
      ERROR,
      new Closure[Response](null) {
        override def call(): Response = {
          response.internalServerError(ERROR.getBody)
        }
      }
    )
  }

  any(QUERY_PARAM.getPath) { request: Request =>
    controller(
      QUERY_PARAM,
      new Closure[Response](null) {
        override def call(): Response = {
          response.ok(QUERY_PARAM.getBody)
        }
      }
    )
  }

  any(EXCEPTION.getPath) { request: Request =>
    controller(
      EXCEPTION,
      new Closure[Future[Response]](null) {
        override def call(): Future[Response] = {
          throw new Exception(EXCEPTION.getBody)
        }
      }
    )
  }

  any(REDIRECT.getPath) { request: Request =>
    controller(
      REDIRECT,
      new Closure[Response](null) {
        override def call(): Response = {
          response.found.location(REDIRECT.getBody)
        }
      }
    )
  }

  any(CAPTURE_HEADERS.getPath) { request: Request =>
    controller(
      CAPTURE_HEADERS,
      new Closure[Response](null) {
        override def call(): Response = {
          response
            .ok(CAPTURE_HEADERS.getBody)
            .header(
              "X-Test-Response",
              request.headerMap.get("X-Test-Request").get
            )
        }
      }
    )
  }

  any(INDEXED_CHILD.getPath) { request: Request =>
    controller(
      INDEXED_CHILD,
      new Closure[Response](null) {
        override def call(): Response = {
          INDEXED_CHILD.collectSpanAttributes(new UrlParameterProvider {
            override def getParameter(name: String): String =
              request.getParam(name)
          })
          response.ok(INDEXED_CHILD.getBody)
        }
      }
    )
  }
}
