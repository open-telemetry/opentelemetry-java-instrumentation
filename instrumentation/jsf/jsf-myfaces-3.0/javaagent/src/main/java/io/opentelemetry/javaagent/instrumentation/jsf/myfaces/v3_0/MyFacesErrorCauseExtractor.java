/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jsf.myfaces.v3_0;

import io.opentelemetry.instrumentation.api.internal.CauseUnwrapper;
import io.opentelemetry.javaagent.instrumentation.jsf.common.jakarta.JsfErrorCauseExtractor;
import jakarta.el.ELException;

class MyFacesErrorCauseExtractor extends JsfErrorCauseExtractor {

  @Override
  public Throwable extract(Throwable error) {
    Throwable unwrapped = super.extract(error);
    return CauseUnwrapper.unwrap(unwrapped, e -> e instanceof ELException);
  }
}
