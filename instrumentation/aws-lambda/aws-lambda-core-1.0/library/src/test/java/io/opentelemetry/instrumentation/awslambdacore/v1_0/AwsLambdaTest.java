/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdacore.v1_0;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AwsLambdaTest extends AbstractAwsLambdaTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Override
  protected RequestHandler<String, String> handler() {
    return new TestRequestHandler(testing.getOpenTelemetrySdk());
  }

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @ParameterizedTest
  @CsvSource({"false,false", "true,false", "false,true", "true,true"})
  void handlerPreservesAndRestoresContext(boolean streamHandler, boolean fail) {
    ContextKey<String> key = ContextKey.named("invocation-value");
    IllegalStateException failure = new IllegalStateException("handler failure");
    Runnable handlerBody =
        () -> {
          assertThat(io.opentelemetry.context.Context.current().get(key)).isEqualTo("preserved");
          assertThat(Span.current().isRecording()).isTrue();
          if (fail) {
            throw failure;
          }
        };
    TracingRequestHandler<String, String> requestHandler =
        new TracingRequestHandler<String, String>(testing.getOpenTelemetrySdk()) {
          @Override
          protected String doHandleRequest(String input, Context context) {
            handlerBody.run();
            return "world";
          }
        };
    TracingRequestStreamHandler requestStreamHandler =
        new TracingRequestStreamHandler(testing.getOpenTelemetrySdk()) {
          @Override
          protected void doHandleRequest(InputStream input, OutputStream output, Context context) {
            handlerBody.run();
          }
        };

    io.opentelemetry.context.Context caller = io.opentelemetry.context.Context.current();
    testing.runWithSpan(
        "ambient",
        () -> {
          io.opentelemetry.context.Context ambient =
              io.opentelemetry.context.Context.current().with(key, "preserved");
          try (Scope ignored = ambient.makeCurrent()) {
            Throwable thrown =
                catchThrowable(
                    () -> {
                      if (streamHandler) {
                        requestStreamHandler.handleRequest(
                            new ByteArrayInputStream(new byte[0]),
                            new ByteArrayOutputStream(),
                            context());
                      } else {
                        assertThat(requestHandler.handleRequest("hello", context()))
                            .isEqualTo("world");
                      }
                    });
            assertThat(thrown).isSameAs(fail ? failure : null);
            assertThat(io.opentelemetry.context.Context.current()).isSameAs(ambient);
          }
        });
    assertThat(io.opentelemetry.context.Context.current()).isSameAs(caller);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("ambient").hasNoParent(),
                span ->
                    span.hasName("my_function")
                        .hasKind(SpanKind.SERVER)
                        .hasParent(trace.getSpan(0))));
  }

  private static class TestRequestHandler extends TracingRequestHandler<String, String> {

    TestRequestHandler(OpenTelemetrySdk openTelemetrySdk) {
      super(openTelemetrySdk);
    }

    @Override
    protected String doHandleRequest(String input, Context context) {
      return AbstractAwsLambdaTest.doHandleRequest(input, context);
    }
  }
}
