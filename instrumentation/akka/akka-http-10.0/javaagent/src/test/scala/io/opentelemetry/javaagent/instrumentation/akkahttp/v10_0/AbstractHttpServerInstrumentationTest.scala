/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.v10_0

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.instrumentation.testing.junit.http.{
  AbstractHttpServerTest,
  HttpServerTestOptions,
  ServerEndpoint
}
import io.opentelemetry.semconv.HttpAttributes

import java.util
import java.util.function.{BiFunction, Function, Predicate}

abstract class AbstractHttpServerInstrumentationTest
    extends AbstractHttpServerTest[Object] {

  override protected def configure(
      options: HttpServerTestOptions
  ): Unit = {
    configure(options, hasRoute = false)
  }

  protected def configure(
      options: HttpServerTestOptions,
      hasRoute: Boolean
  ): Unit = {
    options.setTestCaptureHttpHeaders(false)
    if (!hasRoute) {
      options.setHttpAttributes(
        new Function[ServerEndpoint, util.Set[AttributeKey[_]]] {
          override def apply(v1: ServerEndpoint): util.Set[AttributeKey[_]] = {
            val set = new util.HashSet[AttributeKey[_]](
              HttpServerTestOptions.DEFAULT_HTTP_ATTRIBUTES
            )
            set.remove(HttpAttributes.HTTP_ROUTE)
            set
          }
        }
      )
    }
    options.setHasResponseCustomizer(
      new Predicate[ServerEndpoint] {
        override def test(t: ServerEndpoint): Boolean =
          t != ServerEndpoint.EXCEPTION
      }
    )
    // instrumentation does not create a span at all
    options.disableTestNonStandardHttpMethod
  }

  protected def configureRouteServer(options: HttpServerTestOptions): Unit = {
    configure(options, hasRoute = true)

    options.setTestException(false)
    options.setTestPathParam(true)
    options.setHttpAttributes(
      new Function[ServerEndpoint, util.Set[AttributeKey[_]]] {
        override def apply(v1: ServerEndpoint): util.Set[AttributeKey[_]] =
          HttpServerTestOptions.DEFAULT_HTTP_ATTRIBUTES
      }
    )
    options.setExpectedHttpRoute(
      new BiFunction[ServerEndpoint, String, String] {
        override def apply(
            endpoint: ServerEndpoint,
            method: String
        ): String =
          if (endpoint eq ServerEndpoint.PATH_PARAM) "/path/*/param"
          else expectedHttpRoute(endpoint, method)
      }
    )
  }
}
