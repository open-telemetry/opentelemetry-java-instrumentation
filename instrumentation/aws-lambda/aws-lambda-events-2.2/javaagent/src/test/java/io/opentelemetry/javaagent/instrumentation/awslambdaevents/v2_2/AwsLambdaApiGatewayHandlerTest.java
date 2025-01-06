/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awslambdaevents.v2_2;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.UrlAttributes.URL_FULL;
import static io.opentelemetry.semconv.UserAgentAttributes.USER_AGENT_ORIGINAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.semconv.incubating.FaasIncubatingAttributes;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class AwsLambdaApiGatewayHandlerTest {

  @RegisterExtension
  public static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Mock private Context context;

  @BeforeEach
  void setUp() {
    when(context.getFunctionName()).thenReturn("test_function");
    when(context.getAwsRequestId()).thenReturn("1-22-2024");
  }

  @AfterEach
  void tearDown() {
    assertThat(testing.forceFlushCalled()).isTrue();
  }

  @Test
  void tracedWithHttpPropagation() {
    Map<String, String> headers = new HashMap<>();
    headers.put("traceparent", "00-ee13e7026227ebf4c74278ae29691d7a-0000000000000456-01");
    headers.put("User-Agent", "Clever Client");
    headers.put("host", "localhost:2024");
    headers.put("X-FORWARDED-PROTO", "http");

    APIGatewayProxyRequestEvent input =
        new APIGatewayProxyRequestEvent()
            .withHttpMethod("PUT")
            .withResource("/hello/{param}")
            .withPath("/hello/world")
            .withBody("hello")
            .withHeaders(headers);

    APIGatewayProxyResponseEvent result =
        new TestRequestHandlerApiGateway().handleRequest(input, context);
    assertThat(result.getBody()).isEqualTo("hello world");
    assertThat(result.getStatusCode()).isEqualTo(201);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("PUT /hello/{param}")
                        .hasKind(SpanKind.SERVER)
                        .hasTraceId("ee13e7026227ebf4c74278ae29691d7a")
                        .hasParentSpanId("0000000000000456")
                        .hasAttributesSatisfyingExactly(
                            equalTo(FaasIncubatingAttributes.FAAS_INVOCATION_ID, "1-22-2024"),
                            equalTo(FaasIncubatingAttributes.FAAS_TRIGGER, "http"),
                            equalTo(HTTP_REQUEST_METHOD, "PUT"),
                            equalTo(USER_AGENT_ORIGINAL, "Clever Client"),
                            equalTo(URL_FULL, "http://localhost:2024/hello/world"),
                            equalTo(HTTP_RESPONSE_STATUS_CODE, 201L))));
  }

  public static class TestRequestHandlerApiGateway
      implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    @Override
    public APIGatewayProxyResponseEvent handleRequest(
        APIGatewayProxyRequestEvent input, Context context) {
      return new APIGatewayProxyResponseEvent().withStatusCode(201).withBody("hello world");
    }
  }
}
