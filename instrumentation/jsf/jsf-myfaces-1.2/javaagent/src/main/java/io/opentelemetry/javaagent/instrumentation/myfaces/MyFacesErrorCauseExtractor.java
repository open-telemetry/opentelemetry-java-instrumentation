/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.myfaces;

import io.opentelemetry.javaagent.instrumentation.jsf.JsfErrorCauseExtractor;
import javax.el.ELException;

public class MyFacesErrorCauseExtractor extends JsfErrorCauseExtractor {

  @Override
  public Throwable extract(Throwable error) {
    error = super.extract(error);
    while (error.getCause() != null && error instanceof ELException) {
      error = error.getCause();
    }
    return error;
  }
}
