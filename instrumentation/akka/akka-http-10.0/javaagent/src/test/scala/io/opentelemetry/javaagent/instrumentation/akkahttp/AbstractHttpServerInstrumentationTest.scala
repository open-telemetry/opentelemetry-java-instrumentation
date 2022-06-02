/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp

import io.opentelemetry.api.common.AttributeKey

import collection.JavaConverters._
import io.opentelemetry.instrumentation.testing.junit.http.{
  AbstractHttpServerTest,
  HttpServerTestOptions,
  ServerEndpoint
}

import java.util
import java.util.Collections
import java.util.function.Function

abstract class AbstractHttpServerInstrumentationTest
    extends AbstractHttpServerTest[Object] {

  override protected def configure(
      options: HttpServerTestOptions
  ): Unit = {
    options.setTestCaptureHttpHeaders(false)
    //Akka HTTP actively prevents exceptions from reaching the controller through implicit handleException methods,
    // as unhandled exceptions reaching the HTTP controller would kill the entire HTTP server.
    options.setTestException(false)
    options.setHttpAttributes(
      new Function[ServerEndpoint, util.Set[AttributeKey[_]]] {
        override def apply(v1: ServerEndpoint): util.Set[AttributeKey[_]] =
          Collections.emptySet()
      }
    )
  }
}
