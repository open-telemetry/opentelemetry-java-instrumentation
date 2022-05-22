/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdacore.v1_0;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.when;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.WrappedLambda;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junitpioneer.jupiter.SetEnvironmentVariable;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@SetEnvironmentVariable(
    key = WrappedLambda.OTEL_LAMBDA_HANDLER_ENV_KEY,
    value =
        "io.opentelemetry.instrumentation.awslambdacore.v1_0.AwsLambdaStreamWrapperHttpPropagationTest$TestRequestHandler::handleRequest")
public class AwsLambdaStreamWrapperHttpPropagationTest {

  @RegisterExtension
  public static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Mock private Context context;

  @BeforeEach
  void setUp() {
    when(context.getFunctionName()).thenReturn("my_function");
    when(context.getAwsRequestId()).thenReturn("1-22-333");
    when(context.getInvokedFunctionArn())
        .thenReturn("arn:aws:lambda:us-east-1:123456789:function:test");
  }

  @AfterEach
  void tearDown() {
    assertThat(testing.forceFlushCalled()).isTrue();
  }

  @Test
  void handlerTraced() throws Exception {
    String content =
        "{"
            + "\"headers\" : {"
            + "\"traceparent\": \"00-4fd0b6131f19f39af59518d127b0cafe-0000000000000456-01\""
            + "},"
            + "\"body\" : \"hello\""
            + "}";
    InputStream input = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    OutputStream output = new ByteArrayOutputStream();

    TracingRequestStreamWrapper wrapper =
        new TracingRequestStreamWrapper(
            testing.getOpenTelemetrySdk(), WrappedLambda.fromConfiguration());
    wrapper.handleRequest(input, output, context);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("my_function")
                        .hasKind(SpanKind.SERVER)
                        .hasTraceId("4fd0b6131f19f39af59518d127b0cafe")
                        .hasParentSpanId("0000000000000456")
                        .hasAttributesSatisfying(
                            attrs ->
                                OpenTelemetryAssertions.assertThat(attrs)
                                    .containsOnly(
                                        entry(
                                            ResourceAttributes.FAAS_ID,
                                            "arn:aws:lambda:us-east-1:123456789:function:test"),
                                        entry(ResourceAttributes.CLOUD_ACCOUNT_ID, "123456789"),
                                        entry(SemanticAttributes.FAAS_EXECUTION, "1-22-333")))));
  }

  @Test
  void handlerTracedWithException() {
    String content =
        "{"
            + "\"headers\" : {"
            + "\"traceparent\": \"00-4fd0b6131f19f39af59518d127b0cafe-0000000000000456-01\""
            + "},"
            + "\"body\" : \"bye\""
            + "}";
    InputStream input = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    OutputStream output = new ByteArrayOutputStream();

    TracingRequestStreamWrapper wrapper =
        new TracingRequestStreamWrapper(
            testing.getOpenTelemetrySdk(), WrappedLambda.fromConfiguration());

    Throwable thrown = catchThrowable(() -> wrapper.handleRequest(input, output, context));
    assertThat(thrown).isInstanceOf(IllegalArgumentException.class);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("my_function")
                        .hasKind(SpanKind.SERVER)
                        .hasTraceId("4fd0b6131f19f39af59518d127b0cafe")
                        .hasParentSpanId("0000000000000456")
                        .hasStatus(StatusData.error())
                        .hasException(thrown)
                        .hasAttributesSatisfying(
                            attrs ->
                                OpenTelemetryAssertions.assertThat(attrs)
                                    .containsOnly(
                                        entry(
                                            ResourceAttributes.FAAS_ID,
                                            "arn:aws:lambda:us-east-1:123456789:function:test"),
                                        entry(ResourceAttributes.CLOUD_ACCOUNT_ID, "123456789"),
                                        entry(SemanticAttributes.FAAS_EXECUTION, "1-22-333")))));
  }

  public static final class TestRequestHandler implements RequestStreamHandler {

    private static final JsonFactory JSON_FACTORY = new JsonFactory();

    @Override
    public void handleRequest(InputStream input, OutputStream output, Context context)
        throws IOException {
      String body = "";
      try (JsonParser parser = JSON_FACTORY.createParser(input)) {
        parser.nextToken();
        while (parser.nextToken() != JsonToken.END_OBJECT) {
          parser.nextToken();
          if (!parser.getCurrentName().equals("body")) {
            parser.skipChildren();
            continue;
          }
          body = parser.getText();
          break;
        }
      }
      BufferedWriter writer =
          new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8));
      if (body.equals("hello")) {
        writer.write("world");
        writer.flush();
        writer.close();
      } else {
        throw new IllegalArgumentException("bad argument");
      }
    }
  }
}
