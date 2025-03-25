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

public class NocodeSpanStatusExtractor
    implements SpanStatusExtractor<NocodeMethodInvocation, Object> {
  private static final Logger logger = Logger.getLogger(NocodeSpanStatusExtractor.class.getName());

  @Override
  public void extract(
      SpanStatusBuilder spanStatusBuilder,
      NocodeMethodInvocation mi,
      @Nullable Object returnValue,
      @Nullable Throwable error) {

    if (mi.getRule() == null || mi.getRule().spanStatus == null) {

      // FIXME would love to use a DefaultSpanStatusExtractor as a fallback but it is not public
      // so here is a copy of its (admittedly simple) guts
      if (error != null) {
        spanStatusBuilder.setStatus(StatusCode.ERROR);
      }
      return;
    }
    Object status = mi.evaluateAtEnd(mi.getRule().spanStatus, returnValue, error);
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
