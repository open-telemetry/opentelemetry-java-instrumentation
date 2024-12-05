/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.api.trace.SpanKind.PRODUCER;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PROTOCOL_VERSION;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.UrlAttributes.URL_FULL;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_METHOD;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SERVICE;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SYSTEM;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import com.amazonaws.AmazonWebServiceClient;
import com.amazonaws.SDKGlobalConfiguration;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.AnonymousAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.handlers.RequestHandler2;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.testing.internal.armeria.testing.junit5.server.mock.MockWebServerExtension;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

public abstract class AbstractBaseAwsClientTest {
  protected abstract InstrumentationExtension testing();

  protected abstract boolean hasRequestId();

  protected static MockWebServerExtension server = new MockWebServerExtension();
  protected static AwsClientBuilder.EndpointConfiguration endpoint;
  protected static final AWSStaticCredentialsProvider credentialsProvider =
      new AWSStaticCredentialsProvider(new AnonymousAWSCredentials());

  @BeforeAll
  static void setUp() {
    System.setProperty(SDKGlobalConfiguration.ACCESS_KEY_SYSTEM_PROPERTY, "my-access-key");
    System.setProperty(SDKGlobalConfiguration.SECRET_KEY_SYSTEM_PROPERTY, "my-secret-key");
    server.start();
    endpoint = new AwsClientBuilder.EndpointConfiguration(server.httpUri().toString(), "us-west-2");
  }

  @BeforeEach
  void reset() {
    server.beforeTestExecution(null);
  }

  @AfterAll
  static void cleanUp() {
    System.clearProperty(SDKGlobalConfiguration.ACCESS_KEY_SYSTEM_PROPERTY);
    System.clearProperty(SDKGlobalConfiguration.SECRET_KEY_SYSTEM_PROPERTY);
    server.stop();
  }

  public void assertRequestWithMockedResponse(
      Object response,
      Object client,
      String service,
      String operation,
      String method,
      List<AttributeAssertion> additionalAttributes)
      throws Exception {

    assertThat(response).isNotNull();

    List<RequestHandler2> requestHandler2s = extractRequestHandlers(client);
    assertThat(requestHandler2s).isNotNull();
    assertThat(
            requestHandler2s.stream()
                .filter(h -> "TracingRequestHandler".equals(h.getClass().getSimpleName())))
        .isNotNull();

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> {
                      List<AttributeAssertion> attributes =
                          new ArrayList<>(
                              asList(
                                  equalTo(URL_FULL, server.httpUri().toString()),
                                  equalTo(HTTP_REQUEST_METHOD, method),
                                  equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                                  equalTo(NETWORK_PROTOCOL_VERSION, "1.1"),
                                  equalTo(SERVER_PORT, server.httpPort()),
                                  equalTo(SERVER_ADDRESS, "127.0.0.1"),
                                  equalTo(RPC_SYSTEM, "aws-api"),
                                  satisfies(RPC_SERVICE, v -> v.contains(service)),
                                  equalTo(RPC_METHOD, operation),
                                  equalTo(stringKey("aws.endpoint"), endpoint.getServiceEndpoint()),
                                  equalTo(stringKey("aws.agent"), "java-aws-sdk")));

                      if (hasRequestId()) {
                        attributes.add(
                            satisfies(
                                stringKey("aws.request_id"), v -> v.isInstanceOf(String.class)));
                      }

                      attributes.addAll(additionalAttributes);

                      span.hasName(service + "." + operation)
                          .hasKind(operation.equals("SendMessage") ? PRODUCER : CLIENT)
                          .hasNoParent()
                          .hasAttributesSatisfyingExactly(attributes);
                    }));
  }

  @SuppressWarnings("unchecked")
  protected List<RequestHandler2> extractRequestHandlers(Object client) throws Exception {
    Field requestHandler2sField = AmazonWebServiceClient.class.getDeclaredField("requestHandler2s");
    requestHandler2sField.setAccessible(true);
    return (List<RequestHandler2>) requestHandler2sField.get(client);
  }
}
