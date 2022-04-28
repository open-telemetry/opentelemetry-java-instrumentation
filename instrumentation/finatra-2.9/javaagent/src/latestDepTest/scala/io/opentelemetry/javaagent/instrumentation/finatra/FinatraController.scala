/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.finatra

import com.twitter.finagle.http.{Request, Response}
import com.twitter.finatra.http.Controller
import com.twitter.util.Future
import groovy.lang.Closure
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest.controller
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint._

import java.util.function.Supplier

class FinatraController extends Controller {
  any(SUCCESS.getPath) { request: Request =>
    controller(
      SUCCESS,
      new Supplier[Response] {
        override def get(): Response = {
          response.ok(SUCCESS.getBody)
        }
      }
    )
  }

  any(ERROR.getPath) { request: Request =>
    controller(
      ERROR,
      new Supplier[Response] {
        override def get(): Response = {
          response.internalServerError(ERROR.getBody)
        }
      }
    )
  }

  any(QUERY_PARAM.getPath) { request: Request =>
    controller(
      QUERY_PARAM,
      new Supplier[Response] {
        override def get(): Response = {
          response.ok(QUERY_PARAM.getBody)
        }
      }
    )
  }

  any(EXCEPTION.getPath) { request: Request =>
    controller(
      EXCEPTION,
      new Supplier[Future[Response]] {
        override def get(): Future[Response] = {
          throw new Exception(EXCEPTION.getBody)
        }
      }
    )
  }

  any(REDIRECT.getPath) { request: Request =>
    controller(
      REDIRECT,
      new Supplier[Response] {
        override def get(): Response = {
          response.found.location(REDIRECT.getBody)
        }
      }
    )
  }

  any(CAPTURE_HEADERS.getPath) { request: Request =>
    controller(
      CAPTURE_HEADERS,
      new Supplier[Response] {
        override def get(): Response = {
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
      new Supplier[Response] {
        override def get(): Response = {
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
