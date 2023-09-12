/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2

import io.opentelemetry.instrumentation.api.internal.ConfigPropertiesUtil
import io.opentelemetry.instrumentation.test.InstrumentationSpecification
import io.opentelemetry.semconv.SemanticAttributes
import io.opentelemetry.testing.internal.armeria.common.HttpResponse
import io.opentelemetry.testing.internal.armeria.common.HttpStatus
import io.opentelemetry.testing.internal.armeria.common.MediaType
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.client.builder.SdkClientBuilder
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.*
import io.opentelemetry.testing.internal.armeria.testing.junit5.server.mock.MockWebServerExtension
import spock.lang.Shared
import spock.lang.Unroll

import java.util.concurrent.Future

import static com.google.common.collect.ImmutableMap.of
import static io.opentelemetry.api.trace.SpanKind.CLIENT

@Unroll
abstract class AbstractAws2ClientRecordHttpErrorTest extends InstrumentationSpecification {
  private static final StaticCredentialsProvider CREDENTIALS_PROVIDER = StaticCredentialsProvider
          .create(AwsBasicCredentials.create("my-access-key", "my-secret-key"))

  static boolean isRecordIndividualHttpErrorEnabled() {
    // See io.opentelemetry.instrumentation.awssdk.v2_2.autoconfigure.TracingExecutionInterceptor
    return ConfigPropertiesUtil.getBoolean("otel.instrumentation.aws-sdk.experimental-record-individual-http-error", false)
  }

  @Shared
  def server = new MockWebServerExtension()

  def setupSpec() {
    server.start()
  }

  def cleanupSpec() {
    server.stop()
  }

  def setup() {
    server.beforeTestExecution(null)
  }

  void configureSdkClient(SdkClientBuilder builder) {
    builder.overrideConfiguration(createOverrideConfigurationBuilder().build())
  }

  abstract ClientOverrideConfiguration.Builder createOverrideConfigurationBuilder();

  def "send DynamoDB #operation request with retries with builder #builder.class.getName() mocked response"() {
    setup:
    configureSdkClient(builder)
    def client = builder
            .endpointOverride(server.httpUri())
            .region(Region.AP_NORTHEAST_1)
            .credentialsProvider(CREDENTIALS_PROVIDER)
            .build()
    def errorMsg1 = "DynamoDB could not process your request"
    server.enqueue(HttpResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, MediaType.PLAIN_TEXT_UTF_8, errorMsg1))
    def errorMsg2 = "DynamoDB is currently unavailable"
    server.enqueue(HttpResponse.of(HttpStatus.SERVICE_UNAVAILABLE, MediaType.PLAIN_TEXT_UTF_8, errorMsg2))
    server.enqueue(HttpResponse.of(HttpStatus.OK, MediaType.PLAIN_TEXT_UTF_8, ""))
    def response = call.call(client)

    if (response instanceof Future) {
      response = response.get()
    }

    expect:
    response != null
    response.class.simpleName.startsWith(operation)
    assertDynamoDbRequestWithRetry(service, operation, path, method, requestId)

    where:
    [service, operation, method, path, requestId, builder, call] << dynamoDbRequestWithRetries(DynamoDbClient.builder())
  }


  def assertDynamoDbRequestWithRetry(service, operation, path, method, requestId) {
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "$service.$operation"
          kind CLIENT
          hasNoParent()
          attributes {
            "$SemanticAttributes.NET_PEER_NAME" "127.0.0.1"
            "$SemanticAttributes.NET_PEER_PORT" server.httpPort()
            "$SemanticAttributes.HTTP_URL" { it.startsWith("${server.httpUri()}${path}") }
            "$SemanticAttributes.HTTP_METHOD" "$method"
            "$SemanticAttributes.HTTP_STATUS_CODE" 200
            "$SemanticAttributes.USER_AGENT_ORIGINAL" { it.startsWith("aws-sdk-java/") }
            "$SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH" { it == null || it instanceof Long }
            "$SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH" { it == null || it instanceof Long }
            "$SemanticAttributes.RPC_SYSTEM" "aws-api"
            "$SemanticAttributes.RPC_SERVICE" "$service"
            "$SemanticAttributes.RPC_METHOD" "${operation}"
            "aws.agent" "java-aws-sdk"
            "aws.requestId" "$requestId"
            "aws.table.name" "sometable"
            "$SemanticAttributes.DB_SYSTEM" "dynamodb"
            "$SemanticAttributes.DB_OPERATION" "${operation}"
          }
          if (isRecordIndividualHttpErrorEnabled()) {
            events(2)
            event(0) {
              eventName "HTTP request failure"
              attributes {
                "$SemanticAttributes.HTTP_RESPONSE_STATUS_CODE" 500
                "aws.http.error_message" "DynamoDB could not process your request"
              }
            }
            event(1) {
              eventName "HTTP request failure"
              attributes {
                "$SemanticAttributes.HTTP_RESPONSE_STATUS_CODE" 503
                "aws.http.error_message" "DynamoDB is currently unavailable"
              }
            }
          } else {
            events(0)
          }
        }
      }
    }
    server.takeRequest()
    def request = server.takeRequest()
    request.request().headers().get("X-Amzn-Trace-Id") != null
    request.request().headers().get("traceparent") == null
  }


  static dynamoDbRequestWithRetries(client) {
    [
            ["DynamoDb", "PutItem", "POST", "/", "UNKNOWN", client,
             { c -> c.putItem(PutItemRequest.builder().tableName("sometable").item(of("key", val("value"), "attributeOne", val("one"), "attributeTwo", val("two"))).conditionExpression("attributeOne <> :someVal").build()) }]
    ]
  }

  static val(String value) {
    return AttributeValue.builder().s(value).build()
  }
}
