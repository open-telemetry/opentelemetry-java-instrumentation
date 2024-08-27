/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.jsr;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicReference;
import javax.batch.api.chunk.ItemReader;

class SingleItemReader implements ItemReader {
  @Override
  public void open(Serializable serializable) {}

  @Override
  public void close() {}

  @Override
  public Object readItem() {
    return item.getAndSet(null);
  }

  @Override
  public Serializable checkpointInfo() {
    return null;
  }

  public final AtomicReference<String> getItem() {
    return item;
  }

  private final AtomicReference<String> item = new AtomicReference<String>("42");
}
