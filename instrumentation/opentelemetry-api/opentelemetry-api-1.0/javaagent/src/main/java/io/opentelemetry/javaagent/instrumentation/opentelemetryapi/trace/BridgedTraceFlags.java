/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace;

import application.io.opentelemetry.api.trace.TraceFlags;

final class BridgedTraceFlags implements TraceFlags, io.opentelemetry.api.trace.TraceFlags {

  private static final BridgedTraceFlags[] INSTANCES = buildInstances();

  static BridgedTraceFlags toAgent(TraceFlags applicationTraceFlags) {
    if (applicationTraceFlags instanceof BridgedTraceFlags) {
      return (BridgedTraceFlags) applicationTraceFlags;
    }
    return INSTANCES[applicationTraceFlags.asByte() & 255];
  }

  static BridgedTraceFlags fromAgent(io.opentelemetry.api.trace.TraceFlags agentTraceFlags) {
    if (agentTraceFlags instanceof BridgedTraceFlags) {
      return (BridgedTraceFlags) agentTraceFlags;
    }
    return INSTANCES[agentTraceFlags.asByte() & 255];
  }

  private final TraceFlags delegate;

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

  private static BridgedTraceFlags[] buildInstances() {
    BridgedTraceFlags[] instances = new BridgedTraceFlags[256];
    for (int i = 0; i < 256; i++) {
      instances[i] = new BridgedTraceFlags(TraceFlags.fromByte((byte) i));
    }
    return instances;
  }

  private BridgedTraceFlags(TraceFlags delegate) {
    this.delegate = delegate;
  }
}
