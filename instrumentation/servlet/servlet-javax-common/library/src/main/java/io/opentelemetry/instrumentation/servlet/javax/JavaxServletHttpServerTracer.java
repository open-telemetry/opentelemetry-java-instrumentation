/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.javax;

import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.instrumentation.servlet.ServletHttpServerTracer;
import javax.servlet.http.HttpServletRequest;

public abstract class JavaxServletHttpServerTracer<RESPONSE>
    extends ServletHttpServerTracer<HttpServletRequest, RESPONSE> {
  protected JavaxServletHttpServerTracer(JavaxServletAccessor<RESPONSE> accessor) {
    super(accessor);
  }

  @Override
  protected TextMapGetter<HttpServletRequest> getGetter() {
    return JavaxHttpServletRequestGetter.GETTER;
  }

  @Override
  protected String errorExceptionAttributeName() {
    return "javax.servlet.error.exception";
  }
}
