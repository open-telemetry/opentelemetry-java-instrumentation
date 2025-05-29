/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdacore.v1_0;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.when;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.WrappedLambda;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.semconv.incubating.CloudIncubatingAttributes;
import io.opentelemetry.semconv.incubating.FaasIncubatingAttributes;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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
        "io.opentelemetry.instrumentation.awslambdacore.v1_0.AwsLambdaStreamWrapperTest$TestRequestHandler::handleRequest")
public class AwsLambdaStreamWrapperTest {

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
    InputStream input = new ByteArrayInputStream("hello\n".getBytes(StandardCharsets.UTF_8));
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
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                CloudIncubatingAttributes.CLOUD_RESOURCE_ID,
                                "arn:aws:lambda:us-east-1:123456789:function:test"),
                            equalTo(CloudIncubatingAttributes.CLOUD_ACCOUNT_ID, "123456789"),
                            equalTo(FaasIncubatingAttributes.FAAS_INVOCATION_ID, "1-22-333"))));
  }

  @Test
  void handlerTracedWithException() {
    InputStream input = new ByteArrayInputStream("bye\n".getBytes(StandardCharsets.UTF_8));
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
                        .hasStatus(StatusData.error())
                        .hasException(thrown)
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                CloudIncubatingAttributes.CLOUD_RESOURCE_ID,
                                "arn:aws:lambda:us-east-1:123456789:function:test"),
                            equalTo(CloudIncubatingAttributes.CLOUD_ACCOUNT_ID, "123456789"),
                            equalTo(FaasIncubatingAttributes.FAAS_INVOCATION_ID, "1-22-333"))));
  }

  public static final class TestRequestHandler implements RequestStreamHandler {

    @Override
    public void handleRequest(InputStream input, OutputStream output, Context context)
        throws IOException {
      BufferedReader reader =
          new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
      BufferedWriter writer =
          new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8));
      String line = reader.readLine();
      if (line.equals("hello")) {
        writer.write("world");
        writer.flush();
        writer.close();
      } else {
        throw new IllegalArgumentException("bad argument");
      }
    }
  }
}
