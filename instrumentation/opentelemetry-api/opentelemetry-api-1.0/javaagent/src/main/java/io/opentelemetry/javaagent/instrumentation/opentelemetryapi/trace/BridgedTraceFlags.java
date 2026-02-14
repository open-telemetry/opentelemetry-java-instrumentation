/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace;

import io.opentelemetry.api.trace.TraceFlags;

final class BridgedTraceFlags
    implements application.io.opentelemetry.api.trace.TraceFlags, TraceFlags {

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
