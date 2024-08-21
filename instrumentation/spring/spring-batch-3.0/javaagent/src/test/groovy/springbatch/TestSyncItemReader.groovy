/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package springbatch

import org.springframework.batch.item.ItemReader

import java.util.stream.Collectors
import java.util.stream.IntStream

class TestSyncItemReader implements ItemReader<String> {
  private final Iterator<String> items

  TestSyncItemReader(int max) {
    items = IntStream.range(0, max).mapToObj(String.&valueOf).collect(Collectors.toList()).iterator()
  }

  synchronized String read() {
    if (items.hasNext()) {
      return items.next()
    }
    return null
  }
}
