/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdaevents.v2_2;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.when;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.WrappedLambda;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.semconv.incubating.CloudIncubatingAttributes;
import io.opentelemetry.semconv.incubating.FaasIncubatingAttributes;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junitpioneer.jupiter.SetEnvironmentVariable;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class AwsLambdaWrapperTest {

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
  @SetEnvironmentVariable(
      key = WrappedLambda.OTEL_LAMBDA_HANDLER_ENV_KEY,
      value =
          "io.opentelemetry.instrumentation.awslambdaevents.v2_2.AwsLambdaWrapperTest$TestRequestHandlerString::handleRequest")
  void handlerTraced() throws IOException {
    TracingRequestWrapper wrapper =
        new TracingRequestWrapper(testing.getOpenTelemetrySdk(), WrappedLambda.fromConfiguration());
    Object result = wrapper.handleRequest("hello", context);

    assertThat(result).isEqualTo("world");
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
  @SetEnvironmentVariable(
      key = WrappedLambda.OTEL_LAMBDA_HANDLER_ENV_KEY,
      value =
          "io.opentelemetry.instrumentation.awslambdaevents.v2_2.AwsLambdaWrapperTest$TestRequestHandlerString::handleRequest")
  void handlerTracedWithException() {
    TracingRequestWrapper wrapper =
        new TracingRequestWrapper(testing.getOpenTelemetrySdk(), WrappedLambda.fromConfiguration());
    Throwable thrown = catchThrowable(() -> wrapper.handleRequest("goodbye", context));

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

  @Test
  @SetEnvironmentVariable(
      key = WrappedLambda.OTEL_LAMBDA_HANDLER_ENV_KEY,
      value =
          "io.opentelemetry.instrumentation.awslambdaevents.v2_2.AwsLambdaWrapperTest$TestRequestHandlerInteger::handleRequest")
  void handlerTraced_integer() throws IOException {
    TracingRequestWrapper wrapper =
        new TracingRequestWrapper(testing.getOpenTelemetrySdk(), WrappedLambda.fromConfiguration());
    Object result = wrapper.handleRequest(1, context);

    assertThat(result).isEqualTo("world");
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
  @SetEnvironmentVariable(
      key = WrappedLambda.OTEL_LAMBDA_HANDLER_ENV_KEY,
      value =
          "io.opentelemetry.instrumentation.awslambdaevents.v2_2.AwsLambdaWrapperTest$TestRequestHandlerCustomType::handleRequest")
  void handlerTraced_custom() throws IOException {
    TracingRequestWrapper wrapper =
        new TracingRequestWrapper(testing.getOpenTelemetrySdk(), WrappedLambda.fromConfiguration());
    CustomType ct = new CustomType();
    ct.key = "hello there";
    ct.value = "General Kenobi";
    Object result = wrapper.handleRequest(ct, context);

    assertThat(result).isEqualTo("General Kenobi");
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

  public static final class TestRequestHandlerString implements RequestHandler<String, String> {

    @Override
    public String handleRequest(String input, Context context) {
      if (input.equals("hello")) {
        return "world";
      }
      throw new IllegalArgumentException("bad argument");
    }
  }

  public static final class TestRequestHandlerInteger implements RequestHandler<Integer, String> {

    @Override
    public String handleRequest(Integer input, Context context) {
      if (input == 1) {
        return "world";
      }
      throw new IllegalArgumentException("bad argument");
    }
  }

  @SuppressWarnings("UnusedMethod")
  private static class CustomType {
    String key;
    String value;

    // Need getter/setter of all the attributes for serialization/deserialization

    public String getKey() {
      return key;
    }

    public void setKey(String key) {
      this.key = key;
    }

    public String getValue() {
      return value;
    }

    public void setValue(String value) {
      this.value = value;
    }
  }

  public static final class TestRequestHandlerCustomType
      implements RequestHandler<CustomType, String> {

    @Override
    public String handleRequest(CustomType input, Context context) {
      if (input.key.equals("hello there")) {
        return input.value;
      }
      throw new IllegalArgumentException("bad argument");
    }
  }
}
