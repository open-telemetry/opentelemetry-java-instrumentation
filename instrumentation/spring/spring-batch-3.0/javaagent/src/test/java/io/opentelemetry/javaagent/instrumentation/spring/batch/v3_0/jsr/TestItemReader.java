/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.jsr;

import java.io.Serializable;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.batch.api.chunk.ItemReader;

class TestItemReader implements ItemReader {

  private final List<String> items =
      IntStream.range(0, 13).mapToObj(String::valueOf).collect(Collectors.toList());
  private Iterator<String> itemsIt;

  @Override
  public void open(Serializable serializable) {
    itemsIt = items.iterator();
  }

  @Override
  public void close() {
    itemsIt = null;
  }

  @Override
  public Object readItem() {
    if (itemsIt == null) {
      return null;
    }

    return itemsIt.hasNext() ? itemsIt.next() : null;
  }

  @Override
  public Serializable checkpointInfo() {
    return null;
  }
}
