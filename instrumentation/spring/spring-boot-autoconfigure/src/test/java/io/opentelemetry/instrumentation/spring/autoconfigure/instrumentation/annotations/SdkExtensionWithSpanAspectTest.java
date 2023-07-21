/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.instrumentation.annotations;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;

import io.opentelemetry.api.OpenTelemetry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.springframework.core.ParameterNameDiscoverer;

@SuppressWarnings("deprecation") // instrumenting deprecated class for backwards compatibility
class SdkExtensionWithSpanAspectTest extends AbstractWithSpanAspectTest {

  @Override
  WithSpanTester newWithSpanTester() {
    return new SdkExtensionWithSpanTester();
  }

  @Override
  WithSpanAspect newWithSpanAspect(
      OpenTelemetry openTelemetry, ParameterNameDiscoverer parameterNameDiscoverer) {
    return new SdkExtensionWithSpanAspect(openTelemetry, parameterNameDiscoverer);
  }

  static class SdkExtensionWithSpanTester implements WithSpanTester {
    @Override
    @io.opentelemetry.extension.annotations.WithSpan
    public String testWithSpan() {
      return "Span with name testWithSpan was created";
    }

    @Override
    @io.opentelemetry.extension.annotations.WithSpan("greatestSpanEver")
    public String testWithSpanWithValue() {
      return "Span with name greatestSpanEver was created";
    }

    @Override
    @io.opentelemetry.extension.annotations.WithSpan
    public String testWithSpanWithException() throws Exception {
      throw new Exception("Test @WithSpan With Exception");
    }

    @Override
    @io.opentelemetry.extension.annotations.WithSpan(kind = CLIENT)
    public String testWithClientSpan() {
      return "Span with name testWithClientSpan and SpanKind.CLIENT was created";
    }

    @Override
    @io.opentelemetry.extension.annotations.WithSpan
    public CompletionStage<String> testAsyncCompletionStage(CompletionStage<String> stage) {
      return stage;
    }

    @Override
    @io.opentelemetry.extension.annotations.WithSpan
    public CompletableFuture<String> testAsyncCompletableFuture(CompletableFuture<String> stage) {
      return stage;
    }

    @Override
    @io.opentelemetry.extension.annotations.WithSpan
    public String withSpanAttributes(
        @io.opentelemetry.extension.annotations.SpanAttribute String discoveredName,
        @io.opentelemetry.extension.annotations.SpanAttribute String implicitName,
        @io.opentelemetry.extension.annotations.SpanAttribute("explicitName") String parameter,
        @io.opentelemetry.extension.annotations.SpanAttribute("nullAttribute") String nullAttribute,
        String notTraced) {

      return "hello!";
    }
  }
}
