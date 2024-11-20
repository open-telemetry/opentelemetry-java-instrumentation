/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awssdk.v1_11;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.api.trace.SpanKind.PRODUCER;
import static io.opentelemetry.instrumentation.test.utils.PortUtils.UNUSABLE_PORT;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
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
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonWebServiceClient;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.Request;
import com.amazonaws.SDKGlobalConfiguration;
import com.amazonaws.auth.AWSCredentialsProviderChain;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.auth.InstanceProfileCredentialsProvider;
import com.amazonaws.auth.NoOpSigner;
import com.amazonaws.auth.SignerFactory;
import com.amazonaws.auth.SystemPropertiesCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.handlers.RequestHandler2;
import com.amazonaws.retry.PredefinedRetryPolicies;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.rds.AmazonRDSClient;
import com.amazonaws.services.rds.model.DeleteOptionGroupRequest;
import com.amazonaws.services.s3.AmazonS3Client;
import com.google.common.collect.ImmutableMap;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.testing.internal.armeria.common.HttpResponse;
import io.opentelemetry.testing.internal.armeria.common.HttpStatus;
import io.opentelemetry.testing.internal.armeria.common.MediaType;
import io.opentelemetry.testing.internal.armeria.testing.junit5.server.mock.MockWebServerExtension;
import io.opentelemetry.testing.internal.armeria.testing.junit5.server.mock.RecordedRequest;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class Aws0ClientTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final AWSCredentialsProviderChain credentialsProvider =
      new AWSCredentialsProviderChain(
          new EnvironmentVariableCredentialsProvider(),
          new SystemPropertiesCredentialsProvider(),
          new ProfileCredentialsProvider(),
          new InstanceProfileCredentialsProvider());

  private static final MockWebServerExtension server = new MockWebServerExtension();

  @BeforeAll
  static void setUp() {
    System.setProperty(SDKGlobalConfiguration.ACCESS_KEY_SYSTEM_PROPERTY, "my-access-key");
    System.setProperty(SDKGlobalConfiguration.SECRET_KEY_SYSTEM_PROPERTY, "my-secret-key");
    server.start();
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

  private static Stream<Arguments> provideArguments() {
    return Stream.of(Arguments.of(true, 2), Arguments.of(false, 1));
  }

  @ParameterizedTest
  @SuppressWarnings("unchecked")
  @MethodSource("provideArguments")
  void testRequestHandlerIsHookedUpWithConstructor(boolean addHandler, int size) throws Exception {
    BasicAWSCredentials credentials = new BasicAWSCredentials("asdf", "qwerty");
    AmazonS3Client client = new AmazonS3Client(credentials);
    if (addHandler) {
      client.addRequestHandler(new RequestHandler2() {});
    }

    List<RequestHandler2> requestHandler2s = extractRequestHandlers(client);

    assertThat(requestHandler2s).isNotNull();
    assertThat(requestHandler2s.size()).isEqualTo(size);
    assertThat(requestHandler2s.stream().findFirst().get().getClass().getSimpleName())
        .isEqualTo("TracingRequestHandler");
  }

  private static Stream<Arguments> provideSendRequestArguments() {
    return Stream.of(
        Arguments.of(
            new AmazonS3Client().withEndpoint(server.httpUri().toString()),
            "S3",
            "CreateBucket",
            "PUT",
            1,
            (Function<AmazonS3Client, Object>) c -> c.createBucket("testbucket"),
            ImmutableMap.of("aws.bucket.name", "testbucket"),
            ""),
        Arguments.of(
            new AmazonS3Client().withEndpoint(server.httpUri().toString()),
            "S3",
            "GetObject",
            "GET",
            1,
            (Function<AmazonS3Client, Object>) c -> c.getObject("someBucket", "someKey"),
            ImmutableMap.of("aws.bucket.name", "someBucket"),
            ""),
        Arguments.of(
            new AmazonEC2Client().withEndpoint(server.httpUri().toString()),
            "EC2",
            "AllocateAddress",
            "POST",
            4,
            (Function<AmazonEC2Client, Object>) AmazonEC2Client::allocateAddress,
            emptyMap(),
            "<AllocateAddressResponse xmlns=\"http://ec2.amazonaws.com/doc/2016-11-15/\">"
                + "   <requestId>59dbff89-35bd-4eac-99ed-be587EXAMPLE</requestId>"
                + "   <publicIp>192.0.2.1</publicIp>"
                + "   <domain>standard</domain>"
                + "</AllocateAddressResponse>"),
        Arguments.of(
            new AmazonRDSClient().withEndpoint(server.httpUri().toString()),
            "RDS",
            "DeleteOptionGroup",
            "POST",
            1,
            (Function<AmazonRDSClient, Object>)
                c -> c.deleteOptionGroup(new DeleteOptionGroupRequest()),
            emptyMap(),
            "<AllocateAddressResponse xmlns=\"http://ec2.amazonaws.com/doc/2016-11-15/\">"
                + "   <requestId>59dbff89-35bd-4eac-99ed-be587EXAMPLE</requestId>"
                + "   <publicIp>192.0.2.1</publicIp>"
                + "   <domain>standard</domain>"
                + "</AllocateAddressResponse>"));
  }

  @ParameterizedTest
  @MethodSource("provideSendRequestArguments")
  @SuppressWarnings("unchecked")
  void testSendRequestWithMockedResponse(
      AmazonWebServiceClient client,
      String service,
      String operation,
      String method,
      int handlerCount,
      Function<AmazonWebServiceClient, Object> call,
      Map<String, String> additionalAttributes,
      String body)
      throws Exception {
    server.enqueue(HttpResponse.of(HttpStatus.OK, MediaType.PLAIN_TEXT_UTF_8, body));

    Object response = call.apply(client);
    assertThat(response).isNotNull();

    List<RequestHandler2> requestHandler2s = extractRequestHandlers(client);

    assertThat(requestHandler2s).isNotNull();
    assertThat(requestHandler2s.size()).isEqualTo(handlerCount);
    assertThat(requestHandler2s.stream().findFirst().get().getClass().getSimpleName())
        .isEqualTo("TracingRequestHandler");

    testing.waitAndAssertTraces(
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
                              equalTo(stringKey("aws.endpoint"), server.httpUri().toString()),
                              equalTo(stringKey("aws.agent"), "java-aws-sdk")));

                  additionalAttributes.forEach((k, v) -> attributes.add(equalTo(stringKey(k), v)));

                  span.hasName(service + "." + operation)
                      .hasKind(operation.equals("SendMessage") ? PRODUCER : CLIENT)
                      .hasNoParent()
                      .hasAttributesSatisfyingExactly(attributes);
                }));

    RecordedRequest request = server.takeRequest();
    assertThat(request.request().headers().get("X-Amzn-Trace-Id")).isNotNull();
    assertThat(request.request().headers().get("traceparent")).isNull();
  }

  @Test
  void testSendS3RequestToClosedPort() {
    AmazonS3Client client =
        new AmazonS3Client(
                credentialsProvider,
                new ClientConfiguration()
                    .withRetryPolicy(
                        PredefinedRetryPolicies.getDefaultRetryPolicyWithCustomMaxRetries(0)))
            .withEndpoint("http://localhost:" + UNUSABLE_PORT);

    server.enqueue(HttpResponse.of(HttpStatus.OK, MediaType.PLAIN_TEXT_UTF_8, ""));

    Throwable caught = catchThrowable(() -> client.getObject("someBucket", "someKey"));
    assertThat(caught).isInstanceOf(AmazonClientException.class);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("S3.GetObject")
                        .hasKind(CLIENT)
                        .hasStatus(StatusData.error())
                        .hasException(caught)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(URL_FULL, "http://localhost:" + UNUSABLE_PORT),
                            equalTo(HTTP_REQUEST_METHOD, "GET"),
                            equalTo(SERVER_ADDRESS, "localhost"),
                            equalTo(SERVER_PORT, 61),
                            equalTo(RPC_SYSTEM, "aws-api"),
                            equalTo(RPC_SERVICE, "Amazon S3"),
                            equalTo(RPC_METHOD, "GetObject"),
                            equalTo(stringKey("aws.endpoint"), "http://localhost:" + UNUSABLE_PORT),
                            equalTo(stringKey("aws.agent"), "java-aws-sdk"),
                            equalTo(stringKey("aws.bucket.name"), "someBucket"),
                            equalTo(ERROR_TYPE, AmazonClientException.class.getName()))));
  }

  @Test
  void testNaughtyRequestHandlerDoesntBreakTheTrace() {
    AmazonS3Client client = new AmazonS3Client(credentialsProvider);
    client.addRequestHandler(
        new RequestHandler2() {
          @Override
          public void beforeRequest(Request<?> request) {
            throw new IllegalStateException("bad handler");
          }
        });

    Throwable caught = catchThrowable(() -> client.getObject("someBucket", "someKey"));

    assertThat(caught).isInstanceOf(IllegalStateException.class);
    assertThat(Span.current().getSpanContext().isValid()).isFalse();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("S3.GetObject")
                        .hasKind(CLIENT)
                        .hasStatus(StatusData.error())
                        .hasException(caught)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(URL_FULL, "https://s3.amazonaws.com"),
                            equalTo(HTTP_REQUEST_METHOD, "GET"),
                            equalTo(SERVER_ADDRESS, "s3.amazonaws.com"),
                            equalTo(RPC_SYSTEM, "aws-api"),
                            equalTo(RPC_SERVICE, "Amazon S3"),
                            equalTo(RPC_METHOD, "GetObject"),
                            equalTo(stringKey("aws.endpoint"), "https://s3.amazonaws.com"),
                            equalTo(stringKey("aws.agent"), "java-aws-sdk"),
                            equalTo(stringKey("aws.bucket.name"), "someBucket"),
                            equalTo(ERROR_TYPE, IllegalStateException.class.getName()))));
  }

  @Test
  void testTimeoutAndRetryErrorsAreNotCaptured() {
    server.enqueue(HttpResponse.delayed(HttpResponse.of(HttpStatus.OK), Duration.ofSeconds(5)));
    server.enqueue(HttpResponse.delayed(HttpResponse.of(HttpStatus.OK), Duration.ofSeconds(5)));
    AmazonS3Client client =
        new AmazonS3Client(
                new ClientConfiguration()
                    .withRequestTimeout(50 /* ms */)
                    .withRetryPolicy(
                        PredefinedRetryPolicies.getDefaultRetryPolicyWithCustomMaxRetries(1)))
            .withEndpoint(server.httpUri().toString());

    Throwable caught = catchThrowable(() -> client.getObject("someBucket", "someKey"));

    assertThat(caught).isInstanceOf(AmazonClientException.class);
    assertThat(Span.current().getSpanContext().isValid()).isFalse();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("S3.GetObject")
                        .hasKind(CLIENT)
                        .hasStatus(StatusData.error())
                        .hasException(caught)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(URL_FULL, server.httpUri().toString()),
                            equalTo(HTTP_REQUEST_METHOD, "GET"),
                            equalTo(SERVER_PORT, server.httpPort()),
                            equalTo(SERVER_ADDRESS, "127.0.0.1"),
                            equalTo(RPC_SYSTEM, "aws-api"),
                            equalTo(RPC_SERVICE, "Amazon S3"),
                            equalTo(RPC_METHOD, "GetObject"),
                            equalTo(stringKey("aws.endpoint"), server.httpUri().toString()),
                            equalTo(stringKey("aws.agent"), "java-aws-sdk"),
                            equalTo(stringKey("aws.bucket.name"), "someBucket"),
                            equalTo(ERROR_TYPE, AmazonClientException.class.getName()))));
  }

  @Test
  void testCallingGeneratePresignedUrlDoesNotLeakContext() {
    SignerFactory.registerSigner("noop", NoOpSigner.class);
    AmazonS3Client client =
        new AmazonS3Client(new ClientConfiguration().withSignerOverride("noop"))
            .withEndpoint(server.httpUri().toString());

    client.generatePresignedUrl("someBucket", "someKey", new Date());

    assertThat(Span.current().getSpanContext().isValid()).isFalse();
  }

  @SuppressWarnings("unchecked")
  private static List<RequestHandler2> extractRequestHandlers(Object client) throws Exception {
    Field requestHandler2sField = AmazonWebServiceClient.class.getDeclaredField("requestHandler2s");
    requestHandler2sField.setAccessible(true);
    return (List<RequestHandler2>) requestHandler2sField.get(client);
  }
}
