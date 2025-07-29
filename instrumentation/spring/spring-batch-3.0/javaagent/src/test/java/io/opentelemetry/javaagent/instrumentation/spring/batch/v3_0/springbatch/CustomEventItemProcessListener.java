/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.springbatch;

import io.opentelemetry.api.trace.Span;
import org.springframework.batch.core.ItemProcessListener;

public class CustomEventItemProcessListener implements ItemProcessListener<String, Integer> {
  @Override
  public void beforeProcess(String item) {
    Span.current().addEvent("item.process.before");
  }

  @Override
  public void afterProcess(String item, Integer result) {
    Span.current().addEvent("item.process.after");
  }

  @Override
  public void onProcessError(String item, Exception e) {
    Span.current().addEvent("item.process.error");
  }
}
