/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.finatra

import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest.controller
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint._
import io.opentelemetry.instrumentation.testing.util.ThrowingSupplier

class FinatraController extends Controller {
  any(SUCCESS.getPath) { request: Request =>
    controller(
      SUCCESS,
      supplier(() => response.ok(SUCCESS.getBody))
    )
  }

  any(ERROR.getPath) { request: Request =>
    controller(
      ERROR,
      supplier(() => response.internalServerError(ERROR.getBody))
    )
  }

  any(QUERY_PARAM.getPath) { request: Request =>
    controller(
      QUERY_PARAM,
      supplier(() => response.ok(QUERY_PARAM.getBody))
    )
  }

  any(EXCEPTION.getPath) { request: Request =>
    controller(
      EXCEPTION,
      supplier(() => throw new IllegalStateException(EXCEPTION.getBody))
    )
  }

  any(REDIRECT.getPath) { request: Request =>
    controller(
      REDIRECT,
      supplier(() => response.found.location(REDIRECT.getBody))
    )
  }

  any(CAPTURE_HEADERS.getPath) { request: Request =>
    controller(
      CAPTURE_HEADERS,
      supplier(() =>
        response
          .ok(CAPTURE_HEADERS.getBody)
          .header(
            "X-Test-Response",
            request.headerMap.get("X-Test-Request").get
          )
      )
    )
  }

  any(INDEXED_CHILD.getPath) { request: Request =>
    controller(
      INDEXED_CHILD,
      supplier(() => {
        INDEXED_CHILD.collectSpanAttributes(new UrlParameterProvider {
          override def getParameter(name: String): String =
            request.getParam(name)
        })
        response.ok(INDEXED_CHILD.getBody)
      })
    )
  }

  any("/path/:id/param") { request: Request =>
    controller(
      PATH_PARAM,
      supplier(() => response.ok(request.params("id")))
    )
  }

  def supplier[A](action: () => A): ThrowingSupplier[A, Exception] = {
    new ThrowingSupplier[A, Exception] {
      def get(): A = {
        action.apply()
      }
    }
  }
}
