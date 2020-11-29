/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import com.twitter.finagle.http.Request
import com.twitter.finatra.http.HttpServer
import com.twitter.finatra.http.filters.ExceptionMappingFilter
import com.twitter.finatra.http.routing.HttpRouter

class FinatraServer extends HttpServer {
  override protected def configureHttp(router: HttpRouter): Unit = {
    router
      .filter[ExceptionMappingFilter[Request]]
      .add[FinatraController]
      .exceptionMapper[ResponseSettingExceptionMapper]
  }
}
