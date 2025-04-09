/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.nocode;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;

class NocodeSpanKindExtractor implements SpanKindExtractor<NocodeMethodInvocation> {
  @Override
  public SpanKind extract(NocodeMethodInvocation mi) {
    if (mi.getRule() == null || mi.getRule().getSpanKind() == null) {
      return SpanKind.INTERNAL;
    }
    return mi.getRule().getSpanKind();
  }
}
