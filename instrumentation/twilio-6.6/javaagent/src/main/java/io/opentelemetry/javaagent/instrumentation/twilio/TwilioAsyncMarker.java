/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.twilio;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;

/**
 * Twilio async operations just simply call sync ones internally. To suppress duplicate sync
 * telemetry we add a marker entry to the context.
 */
public final class TwilioAsyncMarker {

  private static final ContextKey<Boolean> MARKER_KEY =
      ContextKey.named("opentelemetry-instrumentation-twilio-async-marker");

  public static Context markAsync(Context context) {
    return context.with(MARKER_KEY, Boolean.TRUE);
  }

  public static boolean isMarkedAsync(Context context) {
    return context.get(MARKER_KEY) != null;
  }

  private TwilioAsyncMarker() {}
}
