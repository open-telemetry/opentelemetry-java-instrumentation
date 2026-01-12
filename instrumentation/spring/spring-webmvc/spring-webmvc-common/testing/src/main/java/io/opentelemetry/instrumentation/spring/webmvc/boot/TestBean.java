/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webmvc.boot;

import static io.opentelemetry.instrumentation.spring.webmvc.boot.AbstractSpringBootBasedTest.DEFERRED_RESULT;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.async.DeferredResult;

@Service
public class TestBean {

  private static final Tracer tracer = GlobalOpenTelemetry.getTracer("test");

  @Async
  public void asyncCall(DeferredResult<String> deferredResult) {
    try {
      // We sleep here a bit to give time for the caller of the async method to set up handling
      // for the deferred result. This is needed to ensure that the test deferred result handler
      // span has the expected parent span. Without sleeping here the test can be flaky.
      Thread.sleep(1_000);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
    }
    Span span = tracer.spanBuilder("async-call-child").startSpan();
    try (Scope ignored = span.makeCurrent()) {
      deferredResult.setResult(DEFERRED_RESULT.getBody());
    } finally {
      span.end();
    }
  }
}
