/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.eventbus.v3_0;

/** Whether a Vertx instance, or the event bus that belongs to it, has a tracer registered. */
enum NativeTracingState {
  ENABLED,
  DISABLED;

  static NativeTracingState of(boolean enabled) {
    return enabled ? ENABLED : DISABLED;
  }
}
