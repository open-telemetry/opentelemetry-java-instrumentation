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

import scala.compat.java8.FunctionConverters._

abstract class AbstractHttpServerInstrumentationTest
    extends AbstractHttpServerTest[Object] {

  override final protected def configure(
      options: HttpServerTestOptions
  ): Unit = {
    options.setTestCaptureHttpHeaders(false)
    options.setHttpAttributes(
      ((_: ServerEndpoint) => Set[AttributeKey[_]]().asJava).asJava
    )
  }
}
