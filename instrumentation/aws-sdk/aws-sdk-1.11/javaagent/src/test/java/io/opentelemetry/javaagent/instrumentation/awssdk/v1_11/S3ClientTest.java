/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awssdk.v1_11;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.UrlAttributes.URL_FULL;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_METHOD;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SERVICE;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SYSTEM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.Request;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.NoOpSigner;
import com.amazonaws.auth.SignerFactory;
import com.amazonaws.handlers.RequestHandler2;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.awssdk.v1_11.AbstractS3ClientTest;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class S3ClientTest extends AbstractS3ClientTest {
  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Override
  public AmazonS3ClientBuilder configureClient(AmazonS3ClientBuilder client) {
    return client;
  }

  // Verify agent instruments old and new construction patterns.
  @ParameterizedTest
  @MethodSource("provideS3Arguments")
  void testRequestHandlerIsHookedUpWithBuilder(boolean addHandler, int size, int position)
      throws Exception {
    AmazonS3ClientBuilder builder = AmazonS3ClientBuilder.standard().withRegion(Regions.US_EAST_1);

    if (addHandler) {
      builder.withRequestHandlers(new RequestHandler2() {});
    }
    AmazonS3 client = builder.build();

    List<RequestHandler2> requestHandler2s = extractRequestHandlers(client);
    assertThat(requestHandler2s).isNotNull();
    assertThat(requestHandler2s.size()).isEqualTo(size);
    assertThat(requestHandler2s.get(position).getClass().getSimpleName())
        .isEqualTo("TracingRequestHandler");
  }

  private static Stream<Arguments> provideS3Arguments() {
    return Stream.of(Arguments.of(true, 2, 1), Arguments.of(false, 1, 0));
  }

  @ParameterizedTest
  @MethodSource("provideS3Arguments")
  @SuppressWarnings("deprecation") // AmazonS3Client constructor is deprecated
  void testRequestHandlerIsHookedUpWithConstructor(boolean addHandler, int size) throws Exception {
    BasicAWSCredentials credentials = new BasicAWSCredentials("asdf", "qwerty");
    AmazonS3Client client = new AmazonS3Client(credentials);
    if (addHandler) {
      client.addRequestHandler(new RequestHandler2() {});
    }

    List<RequestHandler2> requestHandler2s = extractRequestHandlers(client);

    assertThat(requestHandler2s).isNotNull();
    assertThat(requestHandler2s.size()).isEqualTo(size);
    assertThat(requestHandler2s.get(0).getClass().getSimpleName())
        .isEqualTo("TracingRequestHandler");
  }

  @Test
  @SuppressWarnings("deprecation") // AmazonS3Client constructor is deprecated
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

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("S3.HeadBucket")
                            .hasKind(CLIENT)
                            .hasStatus(StatusData.error())
                            .hasException(caught)
                            .hasNoParent()
                            .hasAttributesSatisfyingExactly(
                                equalTo(URL_FULL, "https://s3.amazonaws.com"),
                                equalTo(HTTP_REQUEST_METHOD, "HEAD"),
                                equalTo(SERVER_ADDRESS, "s3.amazonaws.com"),
                                equalTo(RPC_SYSTEM, "aws-api"),
                                equalTo(RPC_SERVICE, "Amazon S3"),
                                equalTo(RPC_METHOD, "HeadBucket"),
                                equalTo(stringKey("aws.endpoint"), "https://s3.amazonaws.com"),
                                equalTo(stringKey("aws.agent"), "java-aws-sdk"),
                                equalTo(stringKey("aws.bucket.name"), "someBucket"),
                                equalTo(ERROR_TYPE, IllegalStateException.class.getName()))));
  }

  @Test
  void testCallingGeneratePresignedUrlDoesNotLeakContext() {
    SignerFactory.registerSigner("noop", NoOpSigner.class);
    AmazonS3 client =
        AmazonS3ClientBuilder.standard()
            .withRegion(Regions.US_EAST_1)
            .withClientConfiguration(new ClientConfiguration().withSignerOverride("noop"))
            .build();

    client.generatePresignedUrl("someBucket", "someKey", new Date());

    assertThat(Span.current().getSpanContext().isValid()).isFalse();
  }
}
