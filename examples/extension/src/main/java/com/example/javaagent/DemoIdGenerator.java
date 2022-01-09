/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.javaagent;

import io.opentelemetry.sdk.trace.IdGenerator;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Custom {@link IdGenerator} which provides span and trace ids.
 *
 * @see io.opentelemetry.sdk.trace.SdkTracerProvider
 * @see DemoSdkTracerProviderConfigurer
 */
public class DemoIdGenerator implements IdGenerator {
  private static final AtomicLong traceId = new AtomicLong(0);
  private static final AtomicLong spanId = new AtomicLong(0);

  @Override
  public String generateSpanId() {
    return String.format("%016d", spanId.incrementAndGet());
  }

  @Override
  public String generateTraceId() {
    return String.format("%032d", traceId.incrementAndGet());
  }
}
