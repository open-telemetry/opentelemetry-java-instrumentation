/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.jsr;

import java.io.Serializable;
import javax.batch.api.BatchProperty;
import javax.batch.api.chunk.ItemReader;
import javax.inject.Inject;

class TestPartitionedItemReader implements ItemReader {

  @Inject
  @BatchProperty(name = "start")
  private String startStr;

  @Inject
  @BatchProperty(name = "end")
  private String endStr;

  private int start;
  private int end;

  @Override
  public void open(Serializable checkpoint) {
    start = Integer.parseInt(startStr);
    end = Integer.parseInt(endStr);
  }

  @Override
  public void close() {}

  @Override
  public Object readItem() {
    if (start >= end) {
      return null;
    }

    return String.valueOf(start++);
  }

  @Override
  public Serializable checkpointInfo() {
    return null;
  }
}
