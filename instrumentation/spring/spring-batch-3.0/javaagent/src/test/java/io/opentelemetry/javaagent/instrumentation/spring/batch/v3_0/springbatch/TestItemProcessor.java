/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.springbatch;

import org.springframework.batch.item.ItemProcessor;

public class TestItemProcessor implements ItemProcessor<String, Integer> {
  @Override
  public Integer process(String item) {
    return Integer.parseInt(item);
  }
}
