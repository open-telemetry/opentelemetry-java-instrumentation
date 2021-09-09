/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet;

import io.opentelemetry.instrumentation.api.instrumenter.ErrorCauseExtractor;

public class ServletErrorCauseExtractor<REQUEST, RESPONSE> implements ErrorCauseExtractor {
  private final ServletAccessor<REQUEST, RESPONSE> accessor;

  public ServletErrorCauseExtractor(ServletAccessor<REQUEST, RESPONSE> accessor) {
    this.accessor = accessor;
  }

  @Override
  public Throwable extractCause(Throwable error) {
    if (accessor.isServletException(error) && error.getCause() != null) {
      error = error.getCause();
    }
    return ErrorCauseExtractor.jdk().extractCause(error);
  }
}
