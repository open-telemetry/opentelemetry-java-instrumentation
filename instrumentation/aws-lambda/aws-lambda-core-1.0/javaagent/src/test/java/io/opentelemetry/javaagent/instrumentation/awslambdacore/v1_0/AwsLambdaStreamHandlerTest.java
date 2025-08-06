/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awslambdacore.v1_0;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.when;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AwsLambdaStreamHandlerTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Mock private Context context;

  @BeforeEach
  void setUp() {
    when(context.getFunctionName()).thenReturn("my_function");
    when(context.getAwsRequestId()).thenReturn("1-22-333");
  }

  @AfterEach
  void tearDown() {
    assertThat(testing.forceFlushCalled()).isTrue();
  }

  @Test
  void handlerTraced() throws Exception {
    InputStream input = new ByteArrayInputStream("hello\n".getBytes(StandardCharsets.UTF_8));
    OutputStream output = new ByteArrayOutputStream();
    RequestStreamHandlerTestImpl handler = new RequestStreamHandlerTestImpl();
    handler.handleRequest(input, output, context);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("my_function")
                        .hasKind(SpanKind.SERVER)
                        .hasAttributesSatisfyingExactly(
                            equalTo(FaasIncubatingAttributes.FAAS_INVOCATION_ID, "1-22-333"))));
  }

  @Test
  void handlerTracedWithException() {
    InputStream input = new ByteArrayInputStream("bye\n".getBytes(StandardCharsets.UTF_8));
    OutputStream output = new ByteArrayOutputStream();
    RequestStreamHandlerTestImpl handler = new RequestStreamHandlerTestImpl();

    Throwable thrown = catchThrowable(() -> handler.handleRequest(input, output, context));
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
                            equalTo(FaasIncubatingAttributes.FAAS_INVOCATION_ID, "1-22-333"))));
  }

  static final class RequestStreamHandlerTestImpl implements RequestStreamHandler {
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
