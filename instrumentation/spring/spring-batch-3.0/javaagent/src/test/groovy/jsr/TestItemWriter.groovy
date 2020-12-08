/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package jsr

import javax.batch.api.chunk.ItemWriter

class TestItemWriter implements ItemWriter {
  final List<Integer> items = new ArrayList()

  @Override
  void open(Serializable checkpoint) throws Exception {
  }

  @Override
  void close() throws Exception {
  }

  @Override
  void writeItems(List<Object> items) throws Exception {
    for (item in items) {
      this.items.add(item as Integer)
    }
  }

  @Override
  Serializable checkpointInfo() throws Exception {
    return null
  }
}