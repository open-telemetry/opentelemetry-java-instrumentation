/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.resilience4j.circuitbreaker.v2_0;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.instrumenter.InstrumenterCustomizer;
import io.opentelemetry.instrumentation.api.incubator.instrumenter.InstrumenterCustomizerProvider;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;

public class ThrowingAttributesExtractorProvider implements InstrumenterCustomizerProvider {

  @Override
  public void customize(InstrumenterCustomizer customizer) {
    if (!customizer
        .getInstrumentationName()
        .equals("io.opentelemetry.resilience4j-circuitbreaker-2.0")) {
      return;
    }
    customizer.addAttributesExtractor(
        new AttributesExtractor<Object, Object>() {
          @Override
          public void onStart(
              AttributesBuilder attributes, Context parentContext, Object request) {}

          @Override
          public void onEnd(
              AttributesBuilder attributes,
              Context context,
              Object request,
              Object response,
              Throwable error) {
            if ("true".equals(Baggage.fromContext(context).getEntryValue("test-throw-on-end"))) {
              Span.fromContext(context).setStatus(StatusCode.ERROR);
              throw new AssertionError("telemetry failure");
            }
          }
        });
  }
}
