/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.jsr;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import javax.batch.api.chunk.ItemWriter;

class TestItemWriter implements ItemWriter {
  private final List<Integer> items = new ArrayList<>();

  @Override
  public void open(Serializable checkpoint) {}

  @Override
  public void close() {}

  @Override
  public void writeItems(List<Object> items) {
    for (Object item : items) {
      this.items.add((Integer) item);
    }
  }

  @Override
  public Serializable checkpointInfo() {
    return null;
  }
}
