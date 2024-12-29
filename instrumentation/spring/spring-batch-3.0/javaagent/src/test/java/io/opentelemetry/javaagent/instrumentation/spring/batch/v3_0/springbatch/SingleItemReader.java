/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.springbatch;

import java.util.concurrent.atomic.AtomicReference;
import org.springframework.batch.item.ItemReader;

public class SingleItemReader implements ItemReader<String> {
  final AtomicReference<String> item = new AtomicReference<>("42");

  @Override
  public String read() {
    return item.getAndSet(null);
  }
}
