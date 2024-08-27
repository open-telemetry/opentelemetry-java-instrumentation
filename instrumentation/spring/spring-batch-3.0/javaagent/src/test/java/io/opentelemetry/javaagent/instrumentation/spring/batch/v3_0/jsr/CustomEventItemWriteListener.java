/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.jsr;

import io.opentelemetry.api.trace.Span;
import java.util.List;
import javax.batch.api.chunk.listener.ItemWriteListener;

class CustomEventItemWriteListener implements ItemWriteListener {
  @Override
  public void beforeWrite(List<Object> list) {
    Span.current().addEvent("item.write.before");
  }

  @Override
  public void afterWrite(List<Object> list) {
    Span.current().addEvent("item.write.after");
  }

  @Override
  public void onWriteError(List<Object> list, Exception e) {
    Span.current().addEvent("item.write.error");
  }
}
