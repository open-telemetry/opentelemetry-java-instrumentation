/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.springbatch;

import io.opentelemetry.api.trace.Span;
import org.springframework.batch.core.ItemReadListener;

public class CustomEventItemReadListener implements ItemReadListener<String> {
  @Override
  public void beforeRead() {
    Span.current().addEvent("item.read.before");
  }

  @Override
  public void afterRead(String item) {
    Span.current().addEvent("item.read.after");
  }

  @Override
  public void onReadError(Exception ex) {
    Span.current().addEvent("item.read.error");
  }
}
