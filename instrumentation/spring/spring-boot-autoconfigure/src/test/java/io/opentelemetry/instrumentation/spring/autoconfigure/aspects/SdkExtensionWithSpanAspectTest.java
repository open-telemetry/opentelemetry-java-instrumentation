/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.aspects;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;

import io.opentelemetry.extension.annotations.SpanAttribute;
import io.opentelemetry.extension.annotations.WithSpan;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

class SdkExtensionWithSpanAspectTest extends AbstractWithSpanAspectTest {

  @Override
  WithSpanTester newWithSpanTester() {
    return new SdkExtensionWithSpanTester();
  }

  static class SdkExtensionWithSpanTester implements WithSpanTester {
    @Override
    @WithSpan
    public String testWithSpan() {
      return "Span with name testWithSpan was created";
    }

    @Override
    @WithSpan("greatestSpanEver")
    public String testWithSpanWithValue() {
      return "Span with name greatestSpanEver was created";
    }

    @Override
    @WithSpan
    public String testWithSpanWithException() throws Exception {
      throw new Exception("Test @WithSpan With Exception");
    }

    @Override
    @WithSpan(kind = CLIENT)
    public String testWithClientSpan() {
      return "Span with name testWithClientSpan and SpanKind.CLIENT was created";
    }

    @Override
    @WithSpan
    public CompletionStage<String> testAsyncCompletionStage(CompletionStage<String> stage) {
      return stage;
    }

    @Override
    @WithSpan
    public CompletableFuture<String> testAsyncCompletableFuture(CompletableFuture<String> stage) {
      return stage;
    }

    @Override
    @WithSpan
    public String withSpanAttributes(
        @SpanAttribute String discoveredName,
        @SpanAttribute String implicitName,
        @SpanAttribute("explicitName") String parameter,
        @SpanAttribute("nullAttribute") String nullAttribute,
        String notTraced) {

      return "hello!";
    }
  }
}
