/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.springbatch;

import static java.util.Collections.synchronizedList;

import java.util.ArrayList;
import java.util.List;
import org.springframework.batch.item.ItemWriter;

public class TestItemWriter implements ItemWriter<Integer> {
  final List<Integer> items = synchronizedList(new ArrayList<>());

  @Override
  public void write(List<? extends Integer> items) {
    this.items.addAll(items);
  }
}
