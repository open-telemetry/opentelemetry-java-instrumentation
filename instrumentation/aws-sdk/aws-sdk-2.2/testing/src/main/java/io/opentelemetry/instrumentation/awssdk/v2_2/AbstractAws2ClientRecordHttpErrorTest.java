/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.api.internal.ConfigPropertiesUtil;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.semconv.SemanticAttributes;
import io.opentelemetry.testing.internal.armeria.common.HttpResponse;
import io.opentelemetry.testing.internal.armeria.common.HttpStatus;
import io.opentelemetry.testing.internal.armeria.common.MediaType;
import io.opentelemetry.testing.internal.armeria.testing.junit5.server.mock.MockWebServerExtension;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractAws2ClientRecordHttpErrorTest {
  private static final StaticCredentialsProvider CREDENTIALS_PROVIDER =
      StaticCredentialsProvider.create(
          AwsBasicCredentials.create("my-access-key", "my-secret-key"));

  private static final MockWebServerExtension server = new MockWebServerExtension();
  protected static List<String> httpErrorMessages = new ArrayList<>();

  @BeforeAll
  public static void setupSpec() {
    server.start();
  }

  public static void cleanupSpec() {
    server.stop();
  }

  public abstract ClientOverrideConfiguration.Builder createOverrideConfigurationBuilder();

  protected abstract InstrumentationExtension getTesting();

  // Introducing a new ExecutionInterceptor that's registered with the AWS SDK.
  // It's positioned to execute after the TracingExecutionInterceptor used for SDK instrumentation.
  // The purpose of this interceptor is to inspect the response body of failed HTTP requests.
  // We aim to ensure that the HTTP error message remains accessible in the response body's
  // InputStream
  // even after the TracingExecutionInterceptor has processed it.
  static class ResponseCheckInterceptor implements ExecutionInterceptor {
    @Override
    public Optional<InputStream> modifyHttpResponseContent(
        Context.ModifyHttpResponse context, ExecutionAttributes executionAttributes) {
      Optional<InputStream> responseBody = context.responseBody();
      String errorMsg = extractHttpErrorMessage(context, executionAttributes);
      if (errorMsg != null) {
        return Optional.of(new ByteArrayInputStream(errorMsg.getBytes(Charset.defaultCharset())));
      }
      return responseBody;
    }

    private static String extractHttpErrorMessage(
        Context.AfterTransmission context, ExecutionAttributes executionAttributes) {
      SdkHttpResponse response = context.httpResponse();
      if (executionAttributes == null) {
        return "";
      }

      if (response != null && !response.isSuccessful()) {
        Optional<InputStream> responseBody = context.responseBody();
        if (responseBody.isPresent()) {
          String errorMsg =
              new BufferedReader(
                      new InputStreamReader(responseBody.get(), Charset.defaultCharset()))
                  .lines()
                  .collect(Collectors.joining("\n"));
          httpErrorMessages.add(errorMsg);
          return errorMsg;
        }
      }
      return null;
    }
  }

  private static void cleanResponses() {
    httpErrorMessages.clear();
  }

  public boolean isRecordIndividualHttpErrorEnabled() {
    // See io.opentelemetry.instrumentation.awssdk.v2_2.autoconfigure.TracingExecutionInterceptor
    return ConfigPropertiesUtil.getBoolean(
        "otel.instrumentation.aws-sdk.experimental-record-individual-http-error", false);
  }

  @Test
  // Suppressing deprecation because we use some deprecated attributes in the test
  @SuppressWarnings("deprecation")
  public void testSendDynamoDbRequestWithRetries() {
    cleanResponses();
    // Setup and configuration
    String service = "DynamoDb";
    String operation = "PutItem";
    String method = "POST";
    String requestId = "UNKNOWN";
    DynamoDbClientBuilder builder = DynamoDbClient.builder();
    ClientOverrideConfiguration.Builder overrideConfigBuilder =
        createOverrideConfigurationBuilder()
            .addExecutionInterceptor(new ResponseCheckInterceptor());
    builder.overrideConfiguration(overrideConfigBuilder.build());

    DynamoDbClient client =
        builder
            .endpointOverride(server.httpUri())
            .region(Region.AP_NORTHEAST_1)
            .credentialsProvider(CREDENTIALS_PROVIDER)
            .build();

    // Mocking server responses
    server.enqueue(
        HttpResponse.of(
            HttpStatus.INTERNAL_SERVER_ERROR,
            MediaType.PLAIN_TEXT_UTF_8,
            "DynamoDB could not process your request"));
    server.enqueue(
        HttpResponse.of(
            HttpStatus.SERVICE_UNAVAILABLE,
            MediaType.PLAIN_TEXT_UTF_8,
            "DynamoDB is currently unavailable"));
    server.enqueue(HttpResponse.of(HttpStatus.OK, MediaType.PLAIN_TEXT_UTF_8, ""));

    // Making the call
    client.putItem(PutItemRequest.builder().tableName("sometable").build());

    getTesting()
        .waitAndAssertTraces(
            trace -> {
              trace.hasSpansSatisfyingExactly(
                  span -> {
                    span.hasKind(SpanKind.CLIENT);
                    span.hasNoParent();
                    span.hasAttributesSatisfying(
                        attributes -> {
                          assertThat(attributes)
                              .containsEntry(SemanticAttributes.NET_PEER_NAME, "127.0.0.1")
                              .containsEntry(SemanticAttributes.NET_PEER_PORT, server.httpPort())
                              .containsEntry(SemanticAttributes.HTTP_METHOD, method)
                              .containsEntry(SemanticAttributes.HTTP_STATUS_CODE, 200)
                              .containsEntry(SemanticAttributes.RPC_SYSTEM, "aws-api")
                              .containsEntry(SemanticAttributes.RPC_SERVICE, service)
                              .containsEntry(SemanticAttributes.RPC_METHOD, operation)
                              .containsEntry("aws.agent", "java-aws-sdk")
                              .containsEntry("aws.requestId", requestId)
                              .containsEntry("aws.table.name", "sometable")
                              .containsEntry(SemanticAttributes.DB_SYSTEM, "dynamodb")
                              .containsEntry(SemanticAttributes.DB_OPERATION, operation);
                        });
                    if (isRecordIndividualHttpErrorEnabled()) {
                      span.hasEventsSatisfyingExactly(
                          event ->
                              event
                                  .hasName("HTTP request failure")
                                  .hasAttributesSatisfyingExactly(
                                      equalTo(SemanticAttributes.HTTP_RESPONSE_STATUS_CODE, 500),
                                      equalTo(
                                          AttributeKey.stringKey("aws.http.error_message"),
                                          "DynamoDB could not process your request")),
                          event ->
                              event
                                  .hasName("HTTP request failure")
                                  .hasAttributesSatisfyingExactly(
                                      equalTo(SemanticAttributes.HTTP_RESPONSE_STATUS_CODE, 503),
                                      equalTo(
                                          AttributeKey.stringKey("aws.http.error_message"),
                                          "DynamoDB is currently unavailable")));
                    } else {
                      span.hasEventsSatisfying(events -> assertThat(events.size()).isEqualTo(0));
                    }
                  });
            });

    // make sure the response body input stream is still available and check its content to be
    // expected
    assertThat(httpErrorMessages.size()).isEqualTo(2);
    assertThat(httpErrorMessages.get(0)).isEqualTo("DynamoDB could not process your request");
    assertThat(httpErrorMessages.get(1)).isEqualTo("DynamoDB is currently unavailable");
  }
}
