/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.jsr;

import io.opentelemetry.api.trace.Span;
import javax.batch.api.chunk.listener.ItemReadListener;

class CustomEventItemReadListener implements ItemReadListener {
  @Override
  public void beforeRead() {
    Span.current().addEvent("item.read.before");
  }

  @Override
  public void afterRead(Object o) {
    Span.current().addEvent("item.read.after");
  }

  @Override
  public void onReadError(Exception e) {
    Span.current().addEvent("item.read.error");
  }
}
