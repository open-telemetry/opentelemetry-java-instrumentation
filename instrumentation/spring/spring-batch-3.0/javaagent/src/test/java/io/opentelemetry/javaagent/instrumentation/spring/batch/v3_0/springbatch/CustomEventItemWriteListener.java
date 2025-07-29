/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.springbatch;

import io.opentelemetry.api.trace.Span;
import java.util.List;
import org.springframework.batch.core.ItemWriteListener;

public class CustomEventItemWriteListener implements ItemWriteListener<Integer> {
  @Override
  public void beforeWrite(List<? extends Integer> items) {
    Span.current().addEvent("item.write.before");
  }

  @Override
  public void afterWrite(List<? extends Integer> items) {
    Span.current().addEvent("item.write.after");
  }

  @Override
  public void onWriteError(Exception exception, List<? extends Integer> items) {
    Span.current().addEvent("item.write.error");
  }
}
