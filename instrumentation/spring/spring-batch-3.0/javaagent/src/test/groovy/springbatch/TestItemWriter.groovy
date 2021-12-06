/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package springbatch

import org.springframework.batch.item.ItemWriter

class TestItemWriter implements ItemWriter<Integer> {
  final List<Integer> items = Collections.synchronizedList(new ArrayList())

  @Override
  void write(List<? extends Integer> items) throws Exception {
    this.items.addAll(items)
  }
}