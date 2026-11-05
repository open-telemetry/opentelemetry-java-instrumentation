/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.v10_0;

import akka.event.LoggingAdapter;
import akka.http.ParsingErrorHandler;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import akka.http.scaladsl.model.ErrorInfo;
import akka.http.scaladsl.model.StatusCode;
import akka.http.scaladsl.settings.ServerSettings;

public class CustomParsingErrorHandler extends ParsingErrorHandler {

  @Override
  public HttpResponse handle(
      StatusCode statusCode, ErrorInfo errorInfo, LoggingAdapter log, ServerSettings settings) {
    return HttpResponse.create().withStatus(StatusCodes.IM_A_TEAPOT);
  }
}
