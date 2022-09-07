/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import static io.opentelemetry.javaagent.tooling.HeliosConfiguration.HELIOS_TEST_TRIGGERED_TRACE;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;

public class HeliosProcessor implements SpanProcessor {
  @Override
  public void onStart(Context parentContext, ReadWriteSpan span) {
    Baggage baggage = Baggage.fromContext(parentContext);
    if (baggage.getEntryValue(HELIOS_TEST_TRIGGERED_TRACE) != null) {
      span.setAttribute(HELIOS_TEST_TRIGGERED_TRACE, "true");
    }
  }

  @Override
  public boolean isStartRequired() {
    return true;
  }

  @Override
  public void onEnd(ReadableSpan span) {}

  @Override
  public boolean isEndRequired() {
    return false;
  }
}
