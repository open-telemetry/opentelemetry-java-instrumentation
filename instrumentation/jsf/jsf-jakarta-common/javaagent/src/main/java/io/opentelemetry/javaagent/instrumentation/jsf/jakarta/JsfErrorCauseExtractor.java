/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jsf.jakarta;

import io.opentelemetry.instrumentation.api.instrumenter.ErrorCauseExtractor;
import jakarta.faces.FacesException;

public class JsfErrorCauseExtractor implements ErrorCauseExtractor {
  @Override
  public Throwable extract(Throwable error) {
    while (error.getCause() != null && error instanceof FacesException) {
      error = error.getCause();
    }
    return ErrorCauseExtractor.getDefault().extract(error);
  }
}
