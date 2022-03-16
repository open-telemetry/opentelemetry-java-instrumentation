/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.finatra

import com.twitter.finagle.http.Request
import com.twitter.finatra.http.HttpServer
import com.twitter.finatra.http.filters.ExceptionMappingFilter
import com.twitter.finatra.http.routing.HttpRouter

import java.util.concurrent.{CountDownLatch, TimeUnit}

class FinatraServer extends HttpServer {

  private val latch = new CountDownLatch(1)

  override protected def configureHttp(router: HttpRouter): Unit = {
    router
      .filter[ExceptionMappingFilter[Request]]
      .add[FinatraController]
      .exceptionMapper[ResponseSettingExceptionMapper]
  }

  protected override def postWarmup(): Unit = {
    super.postWarmup()
    latch.countDown()
  }

  def awaitReady(): FinatraServer = {
    latch.await(10, TimeUnit.SECONDS)
    this
  }
}
