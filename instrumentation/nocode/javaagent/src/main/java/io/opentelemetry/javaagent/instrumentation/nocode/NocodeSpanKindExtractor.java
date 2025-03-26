/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.nocode;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import java.util.Locale;

public class NocodeSpanKindExtractor implements SpanKindExtractor<NocodeMethodInvocation> {
  @Override
  public SpanKind extract(NocodeMethodInvocation mi) {
    if (mi.getRule() == null || mi.getRule().getSpanKind() == null) {
      return SpanKind.INTERNAL;
    }
    try {
      return SpanKind.valueOf(mi.getRule().getSpanKind().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException noMatchingValue) {
      return SpanKind.INTERNAL;
    }
  }
}
