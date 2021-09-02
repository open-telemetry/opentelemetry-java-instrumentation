/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package jsr

import javax.batch.api.chunk.ItemReader
import java.util.stream.Collectors
import java.util.stream.IntStream

class TestItemReader implements ItemReader {
  private final List<String> items = IntStream.range(0, 13).mapToObj(String.&valueOf).collect(Collectors.toList())
  private Iterator<String> itemsIt

  @Override
  void open(Serializable serializable) throws Exception {
    itemsIt = items.iterator()
  }

  @Override
  void close() throws Exception {
    itemsIt = null
  }

  @Override
  Object readItem() throws Exception {
    if (itemsIt == null) {
      return null
    }
    return itemsIt.hasNext() ? itemsIt.next() : null
  }

  @Override
  Serializable checkpointInfo() throws Exception {
    return null
  }
}