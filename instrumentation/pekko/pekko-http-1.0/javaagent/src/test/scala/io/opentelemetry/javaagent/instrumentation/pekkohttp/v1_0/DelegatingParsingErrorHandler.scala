/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0

import org.apache.pekko.event.LoggingAdapter
import org.apache.pekko.http.{DefaultParsingErrorHandler, ParsingErrorHandler}
import org.apache.pekko.http.javadsl.model.HttpResponse
import org.apache.pekko.http.scaladsl.model.{ErrorInfo, StatusCode, StatusCodes}
import org.apache.pekko.http.scaladsl.settings.ServerSettings

/** A user written parsing error handler that builds on the default one, so that
  * both of them are instrumented and one runs inside the other.
  */
class DelegatingParsingErrorHandler extends ParsingErrorHandler {
  override def handle(
      status: StatusCode,
      error: ErrorInfo,
      log: LoggingAdapter,
      settings: ServerSettings
  ): HttpResponse =
    DefaultParsingErrorHandler
      .handle(status, error, log, settings)
      .withStatus(StatusCodes.ImATeapot)
}
