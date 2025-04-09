/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.nocode;

import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor;
import java.util.Locale;
import java.util.logging.Logger;
import javax.annotation.Nullable;

class NocodeSpanStatusExtractor implements SpanStatusExtractor<NocodeMethodInvocation, Object> {
  private static final Logger logger = Logger.getLogger(NocodeSpanStatusExtractor.class.getName());

  @Override
  public void extract(
      SpanStatusBuilder spanStatusBuilder,
      NocodeMethodInvocation mi,
      @Nullable Object returnValue,
      @Nullable Throwable error) {

    if (mi.getRule() == null || mi.getRule().getSpanStatus() == null) {
      if (error != null) {
        SpanStatusExtractor.getDefault().extract(spanStatusBuilder, mi, returnValue, error);
      }
      return;
    }
    Object status = mi.evaluateAtEnd(mi.getRule().getSpanStatus(), returnValue, error);
    if (status != null) {
      try {
        StatusCode code = StatusCode.valueOf(status.toString().toUpperCase(Locale.ROOT));
        spanStatusBuilder.setStatus(code);
      } catch (IllegalArgumentException noMatchingValue) {
        // nop, should remain UNSET
        logger.fine("Invalid span status ignored: " + status);
      }
    }
  }
}
