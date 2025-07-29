/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.springbatch;

import java.util.Iterator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.batch.item.ItemReader;

public class TestSyncItemReader implements ItemReader<String> {
  private final Iterator<String> items;

  public TestSyncItemReader(int max) {
    items =
        IntStream.range(0, max).mapToObj(String::valueOf).collect(Collectors.toList()).iterator();
  }

  @Override
  public synchronized String read() {
    if (items.hasNext()) {
      return items.next();
    }
    return null;
  }
}
