/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.springbatch;

import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemStream;

public class TestPartitionedItemReader implements ItemReader<String>, ItemStream {
  ThreadLocal<Integer> start = new ThreadLocal<>();
  ThreadLocal<Integer> end = new ThreadLocal<>();

  @Override
  public String read() {
    if (start.get() >= end.get()) {
      return null;
    }
    Integer value = start.get();
    start.set(value + 1);
    return String.valueOf(value);
  }

  @Override
  public void open(ExecutionContext executionContext) {
    start.set(executionContext.getInt("start"));
    end.set(executionContext.getInt("end"));
  }

  @Override
  public void update(ExecutionContext executionContext) {}

  @Override
  public void close() {}
}
