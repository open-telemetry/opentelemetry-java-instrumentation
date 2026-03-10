/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace;

import io.opentelemetry.api.trace.TraceFlags;

final class BridgedTraceFlags
    implements application.io.opentelemetry.api.trace.TraceFlags, TraceFlags {

  // Bit to indicate that the lower 56 bits of the trace id have been randomly generated with
  // uniform distribution
  private static final byte RANDOM_TRACE_ID_BIT = 0x02;

  private static final BridgedTraceFlags[] INSTANCES = buildInstances();

  static BridgedTraceFlags toAgent(
      application.io.opentelemetry.api.trace.TraceFlags applicationTraceFlags) {
    if (applicationTraceFlags instanceof BridgedTraceFlags) {
      return (BridgedTraceFlags) applicationTraceFlags;
    }
    return INSTANCES[applicationTraceFlags.asByte() & 255];
  }

  static BridgedTraceFlags fromAgent(TraceFlags agentTraceFlags) {
    if (agentTraceFlags instanceof BridgedTraceFlags) {
      return (BridgedTraceFlags) agentTraceFlags;
    }
    return INSTANCES[agentTraceFlags.asByte() & 255];
  }

  private final application.io.opentelemetry.api.trace.TraceFlags delegate;

  @Override
  public boolean isSampled() {
    return delegate.isSampled();
  }

  @Override
  public boolean isTraceIdRandom() {
    // Don't delegate to delegate.isTraceIdRandom() because the application may use an older API
    // version that doesn't have this method
    return (delegate.asByte() & RANDOM_TRACE_ID_BIT) != 0;
  }

  @Override
  public String asHex() {
    return delegate.asHex();
  }

  @Override
  public byte asByte() {
    return delegate.asByte();
  }

  @Override
  public String toString() {
    return delegate.toString();
  }

  private static BridgedTraceFlags[] buildInstances() {
    BridgedTraceFlags[] instances = new BridgedTraceFlags[256];
    for (int i = 0; i < 256; i++) {
      instances[i] =
          new BridgedTraceFlags(
              application.io.opentelemetry.api.trace.TraceFlags.fromByte((byte) i));
    }
    return instances;
  }

  private BridgedTraceFlags(application.io.opentelemetry.api.trace.TraceFlags delegate) {
    this.delegate = delegate;
  }
}
