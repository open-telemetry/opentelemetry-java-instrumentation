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
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.*
import io.opentelemetry.testing.internal.armeria.testing.junit5.server.mock.MockWebServerExtension
import spock.lang.Shared
import spock.lang.Unroll

import java.util.concurrent.Future

import static com.google.common.collect.ImmutableMap.of
import static io.opentelemetry.api.trace.SpanKind.CLIENT


@Unroll
abstract class AbstractAws2ClientCoreTest extends InstrumentationSpecification {
  static boolean isSqsAttributeInjectionEnabled() {
    // See io.opentelemetry.instrumentation.awssdk.v2_2.autoconfigure.TracingExecutionInterceptor
    return ConfigPropertiesUtil.getBoolean("otel.instrumentation.aws-sdk.experimental-use-propagator-for-messaging", false)
  }

  static final StaticCredentialsProvider CREDENTIALS_PROVIDER = StaticCredentialsProvider
    .create(AwsBasicCredentials.create("my-access-key", "my-secret-key"))

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

  def "send DynamoDB #operation request with builder #builder.class.getName() mocked response"() {
    setup:
    configureSdkClient(builder)
    def client = builder
      .endpointOverride(server.httpUri())
      .region(Region.AP_NORTHEAST_1)
      .credentialsProvider(CREDENTIALS_PROVIDER)
      .build()
    server.enqueue(HttpResponse.of(HttpStatus.OK, MediaType.PLAIN_TEXT_UTF_8, ""))
    def response = call.call(client)

    if (response instanceof Future) {
      response = response.get()
    }

    expect:
    response != null
    response.class.simpleName.startsWith(operation)
    switch (operation) {
      case "CreateTable":
        assertCreateTableRequest(path, method, requestId)
        break
      case "Query":
        assertQueryRequest(path, method, requestId)
        break
      default:
        assertDynamoDbRequest(service, operation, path, method, requestId)
    }

    where:
    [service, operation, method, path, requestId, builder, call] << dynamoDbRequestDataTable(DynamoDbClient.builder())
  }

  def "send DynamoDB #operation async request with builder #builder.class.getName() mocked response"() {
    setup:
    configureSdkClient(builder)
    def client = builder
      .endpointOverride(server.httpUri())
      .region(Region.AP_NORTHEAST_1)
      .credentialsProvider(CREDENTIALS_PROVIDER)
      .build()
    server.enqueue(HttpResponse.of(HttpStatus.OK, MediaType.PLAIN_TEXT_UTF_8, ""))
    def response = call.call(client)

    if (response instanceof Future) {
      response = response.get()
    }

    expect:
    response != null
    switch (operation) {
      case "CreateTable":
        assertCreateTableRequest(path, method, requestId)
        break
      case "Query":
        assertQueryRequest(path, method, requestId)
        break
      default:
        assertDynamoDbRequest(service, operation, path, method, requestId)
    }

    where:
    [service, operation, method, path, requestId, builder, call] << dynamoDbRequestDataTable(DynamoDbAsyncClient.builder())
  }

  def assertCreateTableRequest(path, method, requestId) {
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "DynamoDb.CreateTable"
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
            "$SemanticAttributes.RPC_SERVICE" "DynamoDb"
            "$SemanticAttributes.RPC_METHOD" "CreateTable"
            "aws.agent" "java-aws-sdk"
            "aws.requestId" "$requestId"
            "aws.table.name" "sometable"
            "$SemanticAttributes.DB_SYSTEM" "dynamodb"
            "$SemanticAttributes.DB_OPERATION" "CreateTable"
            "aws.dynamodb.global_secondary_indexes" "[{\"IndexName\":\"globalIndex\",\"KeySchema\":[{\"AttributeName\":\"attribute\"}],\"ProvisionedThroughput\":{\"ReadCapacityUnits\":10,\"WriteCapacityUnits\":12}},{\"IndexName\":\"globalIndexSecondary\",\"KeySchema\":[{\"AttributeName\":\"attributeSecondary\"}],\"ProvisionedThroughput\":{\"ReadCapacityUnits\":7,\"WriteCapacityUnits\":8}}]"
            "aws.dynamodb.provisioned_throughput.read_capacity_units" "1"
            "aws.dynamodb.provisioned_throughput.write_capacity_units" "1"
          }
        }
      }
    }
    def request = server.takeRequest()
    request.request().headers().get("X-Amzn-Trace-Id") != null
    request.request().headers().get("traceparent") == null
  }

  def assertQueryRequest(path, method, requestId) {
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "DynamoDb.Query"
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
            "$SemanticAttributes.RPC_SERVICE" "DynamoDb"
            "$SemanticAttributes.RPC_METHOD" "Query"
            "aws.agent" "java-aws-sdk"
            "aws.requestId" "$requestId"
            "aws.table.name" "sometable"
            "$SemanticAttributes.DB_SYSTEM" "dynamodb"
            "$SemanticAttributes.DB_OPERATION" "Query"
            "aws.dynamodb.limit" "10"
            "aws.dynamodb.select" "ALL_ATTRIBUTES"
          }
        }
      }
    }
    def request = server.takeRequest()
    request.request().headers().get("X-Amzn-Trace-Id") != null
    request.request().headers().get("traceparent") == null
  }

  def assertDynamoDbRequest(service, operation, path, method, requestId) {
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
        }
      }
    }
    def request = server.takeRequest()
    request.request().headers().get("X-Amzn-Trace-Id") != null
    request.request().headers().get("traceparent") == null
  }

  static dynamoDbRequestDataTable(client) {
    [
      ["DynamoDb", "CreateTable", "POST", "/", "UNKNOWN", client,
       { c -> c.createTable(createTableRequest()) }],
      ["DynamoDb", "DeleteItem", "POST", "/", "UNKNOWN", client,
       { c -> c.deleteItem(DeleteItemRequest.builder().tableName("sometable").key(of("anotherKey", val("value"), "key", val("value"))).conditionExpression("property in (:one :two)").build()) }],
      ["DynamoDb", "DeleteTable", "POST", "/", "UNKNOWN", client,
       { c -> c.deleteTable(DeleteTableRequest.builder().tableName("sometable").build()) }],
      ["DynamoDb", "GetItem", "POST", "/", "UNKNOWN", client,
       { c -> c.getItem(GetItemRequest.builder().tableName("sometable").key(of("keyOne", val("value"), "keyTwo", val("differentValue"))).attributesToGet("propertyOne", "propertyTwo").build()) }],
      ["DynamoDb", "PutItem", "POST", "/", "UNKNOWN", client,
       { c -> c.putItem(PutItemRequest.builder().tableName("sometable").item(of("key", val("value"), "attributeOne", val("one"), "attributeTwo", val("two"))).conditionExpression("attributeOne <> :someVal").build()) }],
      ["DynamoDb", "Query", "POST", "/", "UNKNOWN", client,
       { c -> c.query(QueryRequest.builder().tableName("sometable").select("ALL_ATTRIBUTES").keyConditionExpression("attribute = :aValue").filterExpression("anotherAttribute = :someVal").limit(10).build()) }],
      ["DynamoDb", "UpdateItem", "POST", "/", "UNKNOWN", client,
       { c -> c.updateItem(UpdateItemRequest.builder().tableName("sometable").key(of("keyOne", val("value"), "keyTwo", val("differentValue"))).conditionExpression("attributeOne <> :someVal").updateExpression("set attributeOne = :updateValue").build()) }]
    ]
  }

  static CreateTableRequest createTableRequest() {
    return CreateTableRequest.builder()
      .tableName("sometable")
      .globalSecondaryIndexes(Arrays.asList(
        GlobalSecondaryIndex.builder()
          .indexName("globalIndex")
          .keySchema(
            KeySchemaElement.builder()
              .attributeName("attribute")
              .build())
          .provisionedThroughput(
            ProvisionedThroughput.builder()
              .readCapacityUnits(10)
              .writeCapacityUnits(12)
              .build()
          )
          .build(),
        GlobalSecondaryIndex.builder()
          .indexName("globalIndexSecondary")
          .keySchema(
            KeySchemaElement.builder()
              .attributeName("attributeSecondary")
              .build())
          .provisionedThroughput(
            ProvisionedThroughput.builder()
              .readCapacityUnits(7)
              .writeCapacityUnits(8)
              .build()
          )
          .build()))
      .provisionedThroughput(
        ProvisionedThroughput.builder()
          .readCapacityUnits(1)
          .writeCapacityUnits(1)
          .build()
      )
      .build()
  }

  static val(String value) {
    return AttributeValue.builder().s(value).build()
  }
}
