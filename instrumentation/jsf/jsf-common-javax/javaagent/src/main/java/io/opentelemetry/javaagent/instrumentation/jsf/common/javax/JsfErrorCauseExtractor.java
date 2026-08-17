/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jsf.common.javax;

import io.opentelemetry.instrumentation.api.instrumenter.ErrorCauseExtractor;
import io.opentelemetry.instrumentation.api.internal.CauseUnwrapper;
import javax.faces.FacesException;

public class JsfErrorCauseExtractor implements ErrorCauseExtractor {

  @Override
  public Throwable extract(Throwable error) {
    Throwable unwrapped = CauseUnwrapper.unwrapCause(error, e -> e instanceof FacesException);
    return ErrorCauseExtractor.getDefault().extract(unwrapped);
  }
}
