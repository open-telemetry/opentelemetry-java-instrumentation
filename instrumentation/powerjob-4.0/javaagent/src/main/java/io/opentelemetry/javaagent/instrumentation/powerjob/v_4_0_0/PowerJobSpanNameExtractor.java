/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.powerjob.v_4_0_0;

import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;

class PowerJobSpanNameExtractor implements SpanNameExtractor<PowerJobProcessRequest> {
  private final SpanNameExtractor<PowerJobProcessRequest> codeSpanNameExtractor;

  PowerJobSpanNameExtractor(CodeAttributesGetter<PowerJobProcessRequest> getter) {
    codeSpanNameExtractor = CodeSpanNameExtractor.create(getter);
  }

  @Override
  public String extract(PowerJobProcessRequest request) {
    // TODO: 2024/8/6 script call
    return codeSpanNameExtractor.extract(request);
  }
}
