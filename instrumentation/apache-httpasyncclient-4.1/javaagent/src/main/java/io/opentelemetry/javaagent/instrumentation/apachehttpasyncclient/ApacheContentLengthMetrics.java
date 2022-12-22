/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpasyncclient;

import java.util.concurrent.atomic.LongAdder;

public class ApacheContentLengthMetrics {
  private final LongAdder requestLength = new LongAdder();

  private final LongAdder responseLength = new LongAdder();

  public void addRequestBytes(int byteLength) {
    requestLength.add(byteLength);
  }

  public void addResponseBytes(int byteLength) {
    responseLength.add(byteLength);
  }

  public long getRequestBytes() {
    return requestLength.sum();
  }

  public long getResponseBytes() {
    return responseLength.sum();
  }
}
