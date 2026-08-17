/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jsf.myfaces.v1_2;

import io.opentelemetry.instrumentation.api.internal.CauseUnwrapper;
import io.opentelemetry.javaagent.instrumentation.jsf.common.javax.JsfErrorCauseExtractor;
import javax.el.ELException;

final class MyFacesErrorCauseExtractor extends JsfErrorCauseExtractor {

  @Override
  public Throwable extract(Throwable error) {
    Throwable unwrapped = super.extract(error);
    return CauseUnwrapper.unwrapCause(unwrapped, e -> e instanceof ELException);
  }
}
