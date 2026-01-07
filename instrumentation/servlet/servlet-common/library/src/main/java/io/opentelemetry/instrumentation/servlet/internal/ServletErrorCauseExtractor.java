/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.internal;

import io.opentelemetry.instrumentation.api.instrumenter.ErrorCauseExtractor;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class ServletErrorCauseExtractor<REQUEST, RESPONSE> implements ErrorCauseExtractor {
  private final ServletAccessor<REQUEST, RESPONSE> accessor;

  public ServletErrorCauseExtractor(ServletAccessor<REQUEST, RESPONSE> accessor) {
    this.accessor = accessor;
  }

  @Override
  public Throwable extract(Throwable error) {
    if (accessor.isServletException(error) && error.getCause() != null) {
      error = error.getCause();
    }
    return ErrorCauseExtractor.getDefault().extract(error);
  }
}
