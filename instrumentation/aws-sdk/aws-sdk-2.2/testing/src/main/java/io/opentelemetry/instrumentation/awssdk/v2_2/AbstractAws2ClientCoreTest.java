/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.testing.junit.db.DbClientMetricsTestUtil.assertDurationMetric;
import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStableDbSystemName;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.UrlAttributes.URL_FULL;
import static io.opentelemetry.semconv.incubating.AwsIncubatingAttributes.AWS_REQUEST_ID;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_METHOD;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SERVICE;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SYSTEM;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableMap;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.api.internal.ConfigPropertiesUtil;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.testing.internal.armeria.common.HttpResponse;
import io.opentelemetry.testing.internal.armeria.common.HttpStatus;
import io.opentelemetry.testing.internal.armeria.common.MediaType;
import io.opentelemetry.testing.internal.armeria.testing.junit5.server.mock.MockWebServerExtension;
import io.opentelemetry.testing.internal.armeria.testing.junit5.server.mock.RecordedRequest;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.builder.SdkClientBuilder;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClientBuilder;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.DeleteTableRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndex;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughput;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

public abstract class AbstractAws2ClientCoreTest {
  protected static final StaticCredentialsProvider CREDENTIALS_PROVIDER =
      StaticCredentialsProvider.create(
          AwsBasicCredentials.create("my-access-key", "my-secret-key"));

  protected static final MockWebServerExtension server = new MockWebServerExtension();

  protected abstract InstrumentationExtension getTesting();

  protected abstract ClientOverrideConfiguration.Builder createOverrideConfigurationBuilder();

  protected static boolean isSqsAttributeInjectionEnabled() {
    // See io.opentelemetry.instrumentation.awssdk.v2_2.autoconfigure.TracingExecutionInterceptor
    return ConfigPropertiesUtil.getBoolean(
        "otel.instrumentation.aws-sdk.experimental-use-propagator-for-messaging", false);
  }

  protected void configureSdkClient(SdkClientBuilder<?, ?> builder) {
    builder.overrideConfiguration(createOverrideConfigurationBuilder().build());
  }

  @BeforeAll
  static void setup() {
    server.start();
  }

  @AfterAll
  static void cleanup() {
    server.stop();
  }

  @BeforeEach
  void prepTest() {
    server.beforeTestExecution(null);
  }

  private void validateOperationResponse(String operation, Object response) {
    assertThat(response).isNotNull();
    assertThat(response.getClass().getSimpleName()).startsWith(operation);

    RecordedRequest request = server.takeRequest();
    assertThat(request).isNotNull();
    assertThat(request.request().headers().get("X-Amzn-Trace-Id")).isNotNull();
    assertThat(request.request().headers().get("traceparent")).isNull();

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> {
                      if (operation.equals("CreateTable")) {
                        assertCreateTableRequest(span);
                      } else if (operation.equals("Query")) {
                        assertQueryRequest(span);
                      } else {
                        assertDynamoDbRequest(span, operation);
                      }
                    }));

    assertDurationMetric(
        getTesting(), "io.opentelemetry.aws-sdk-2.2", DB_SYSTEM_NAME, DB_OPERATION_NAME);
  }

  private static CreateTableRequest createTableRequest() {
    return CreateTableRequest.builder()
        .tableName("sometable")
        .globalSecondaryIndexes(
            Arrays.asList(
                GlobalSecondaryIndex.builder()
                    .indexName("globalIndex")
                    .keySchema(KeySchemaElement.builder().attributeName("attribute").build())
                    .provisionedThroughput(
                        ProvisionedThroughput.builder()
                            .readCapacityUnits(10L)
                            .writeCapacityUnits(12L)
                            .build())
                    .build(),
                GlobalSecondaryIndex.builder()
                    .indexName("globalIndexSecondary")
                    .keySchema(
                        KeySchemaElement.builder().attributeName("attributeSecondary").build())
                    .provisionedThroughput(
                        ProvisionedThroughput.builder()
                            .readCapacityUnits(7L)
                            .writeCapacityUnits(8L)
                            .build())
                    .build()))
        .provisionedThroughput(
            ProvisionedThroughput.builder().readCapacityUnits(1L).writeCapacityUnits(1L).build())
        .build();
  }

  @SuppressWarnings("deprecation") // uses deprecated semconv
  private static void assertCreateTableRequest(SpanDataAssert span) {
    span.hasName("DynamoDb.CreateTable")
        .hasKind(SpanKind.CLIENT)
        .hasNoParent()
        .hasAttributesSatisfyingExactly(
            equalTo(SERVER_ADDRESS, "127.0.0.1"),
            equalTo(SERVER_PORT, server.httpPort()),
            equalTo(URL_FULL, server.httpUri() + "/"),
            equalTo(HTTP_REQUEST_METHOD, "POST"),
            equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
            equalTo(RPC_SYSTEM, "aws-api"),
            equalTo(RPC_SERVICE, "DynamoDb"),
            equalTo(RPC_METHOD, "CreateTable"),
            equalTo(stringKey("aws.agent"), "java-aws-sdk"),
            equalTo(AWS_REQUEST_ID, "UNKNOWN"),
            equalTo(stringKey("aws.table.name"), "sometable"),
            equalTo(maybeStable(DB_SYSTEM), maybeStableDbSystemName("dynamodb")),
            equalTo(maybeStable(DB_OPERATION), "CreateTable"),
            equalTo(
                stringKey("aws.dynamodb.global_secondary_indexes"),
                "[{\"IndexName\":\"globalIndex\",\"KeySchema\":[{\"AttributeName\":\"attribute\"}],\"ProvisionedThroughput\":{\"ReadCapacityUnits\":10,\"WriteCapacityUnits\":12}},{\"IndexName\":\"globalIndexSecondary\",\"KeySchema\":[{\"AttributeName\":\"attributeSecondary\"}],\"ProvisionedThroughput\":{\"ReadCapacityUnits\":7,\"WriteCapacityUnits\":8}}]"),
            equalTo(stringKey("aws.dynamodb.provisioned_throughput.read_capacity_units"), "1"),
            equalTo(stringKey("aws.dynamodb.provisioned_throughput.write_capacity_units"), "1"));
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  private static void assertQueryRequest(SpanDataAssert span) {
    span.hasName("DynamoDb.Query")
        .hasKind(SpanKind.CLIENT)
        .hasNoParent()
        .hasAttributesSatisfyingExactly(
            equalTo(SERVER_ADDRESS, "127.0.0.1"),
            equalTo(SERVER_PORT, server.httpPort()),
            equalTo(URL_FULL, server.httpUri() + "/"),
            equalTo(HTTP_REQUEST_METHOD, "POST"),
            equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
            equalTo(RPC_SYSTEM, "aws-api"),
            equalTo(RPC_SERVICE, "DynamoDb"),
            equalTo(RPC_METHOD, "Query"),
            equalTo(stringKey("aws.agent"), "java-aws-sdk"),
            equalTo(AWS_REQUEST_ID, "UNKNOWN"),
            equalTo(stringKey("aws.table.name"), "sometable"),
            equalTo(maybeStable(DB_SYSTEM), maybeStableDbSystemName("dynamodb")),
            equalTo(maybeStable(DB_OPERATION), "Query"),
            equalTo(stringKey("aws.dynamodb.limit"), "10"),
            equalTo(stringKey("aws.dynamodb.select"), "ALL_ATTRIBUTES"));
  }

  @SuppressWarnings("deprecation") // uses deprecated semconv
  private static void assertDynamoDbRequest(SpanDataAssert span, String operation) {
    span.hasName("DynamoDb." + operation)
        .hasKind(SpanKind.CLIENT)
        .hasNoParent()
        .hasAttributesSatisfyingExactly(
            equalTo(SERVER_ADDRESS, "127.0.0.1"),
            equalTo(SERVER_PORT, server.httpPort()),
            equalTo(URL_FULL, server.httpUri() + "/"),
            equalTo(HTTP_REQUEST_METHOD, "POST"),
            equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
            equalTo(RPC_SYSTEM, "aws-api"),
            equalTo(RPC_SERVICE, "DynamoDb"),
            equalTo(RPC_METHOD, operation),
            equalTo(stringKey("aws.agent"), "java-aws-sdk"),
            equalTo(AWS_REQUEST_ID, "UNKNOWN"),
            equalTo(stringKey("aws.table.name"), "sometable"),
            equalTo(maybeStable(DB_SYSTEM), maybeStableDbSystemName("dynamodb")),
            equalTo(maybeStable(DB_OPERATION), operation));
  }

  @SuppressWarnings("unchecked")
  protected static <T, U> T wrapClient(
      Class<T> syncClientClass, Class<U> asyncClientClass, U asyncClient) {
    return (T)
        Proxy.newProxyInstance(
            AbstractAws2ClientCoreTest.class.getClassLoader(),
            new Class<?>[] {syncClientClass},
            (proxy, method, args) -> {
              Method asyncMethod =
                  asyncClientClass.getMethod(method.getName(), method.getParameterTypes());
              CompletableFuture<?> future =
                  (CompletableFuture<?>) asyncMethod.invoke(asyncClient, args);
              return future.get();
            });
  }

  private static Stream<Arguments> provideArguments() {
    return Stream.of(
        Arguments.of(
            "CreateTable",
            (Function<DynamoDbClient, Object>) c -> c.createTable(createTableRequest())),
        Arguments.of(
            "DeleteItem",
            (Function<DynamoDbClient, Object>)
                c ->
                    c.deleteItem(
                        DeleteItemRequest.builder()
                            .tableName("sometable")
                            .key(
                                ImmutableMap.of(
                                    "anotherKey", AttributeValue.builder().s("value").build(),
                                    "key", AttributeValue.builder().s("value").build()))
                            .conditionExpression("property in (:one, :two)")
                            .build())),
        Arguments.of(
            "DeleteTable",
            (Function<DynamoDbClient, Object>)
                c -> c.deleteTable(DeleteTableRequest.builder().tableName("sometable").build())),
        Arguments.of(
            "GetItem",
            (Function<DynamoDbClient, Object>)
                c ->
                    c.getItem(
                        GetItemRequest.builder()
                            .tableName("sometable")
                            .key(
                                ImmutableMap.of(
                                    "keyOne", AttributeValue.builder().s("value").build(),
                                    "keyTwo", AttributeValue.builder().s("differentValue").build()))
                            .attributesToGet("propertyOne", "propertyTwo")
                            .build())),
        Arguments.of(
            "PutItem",
            (Function<DynamoDbClient, Object>)
                c ->
                    c.putItem(
                        PutItemRequest.builder()
                            .tableName("sometable")
                            .item(
                                ImmutableMap.of(
                                    "key", AttributeValue.builder().s("value").build(),
                                    "attributeOne", AttributeValue.builder().s("one").build(),
                                    "attributeTwo", AttributeValue.builder().s("two").build()))
                            .conditionExpression("attributeOne <> :someVal")
                            .build())),
        Arguments.of(
            "Query",
            (Function<DynamoDbClient, Object>)
                c ->
                    c.query(
                        QueryRequest.builder()
                            .tableName("sometable")
                            .select("ALL_ATTRIBUTES")
                            .keyConditionExpression("attribute = :aValue")
                            .filterExpression("anotherAttribute = :someVal")
                            .limit(10)
                            .build())),
        Arguments.of(
            "UpdateItem",
            (Function<DynamoDbClient, Object>)
                c ->
                    c.updateItem(
                        UpdateItemRequest.builder()
                            .tableName("sometable")
                            .key(
                                ImmutableMap.of(
                                    "keyOne",
                                    AttributeValue.builder().s("value").build(),
                                    "keyTwo",
                                    AttributeValue.builder().s("differentValue").build()))
                            .conditionExpression("attributeOne <> :someVal")
                            .updateExpression("set attributeOne = :updateValue")
                            .build())));
  }

  @ParameterizedTest
  @MethodSource("provideArguments")
  void testSendDynamoDbRequestWithBuilderAndMockedResponse(
      String operation, Function<DynamoDbClient, Object> call) {
    DynamoDbClientBuilder builder = DynamoDbClient.builder();
    configureSdkClient(builder);
    DynamoDbClient client =
        builder
            .endpointOverride(server.httpUri())
            .region(Region.AP_NORTHEAST_1)
            .credentialsProvider(CREDENTIALS_PROVIDER)
            .build();
    server.enqueue(HttpResponse.of(HttpStatus.OK, MediaType.PLAIN_TEXT_UTF_8, ""));
    Object response = call.apply(client);
    validateOperationResponse(operation, response);
  }

  @ParameterizedTest
  @MethodSource("provideArguments")
  void testSendDynamoDbAsyncRequestWithBuilderAndMockedResponse(
      String operation, Function<DynamoDbClient, Object> call) {
    DynamoDbAsyncClientBuilder builder = DynamoDbAsyncClient.builder();
    configureSdkClient(builder);
    DynamoDbAsyncClient client =
        builder
            .endpointOverride(server.httpUri())
            .region(Region.AP_NORTHEAST_1)
            .credentialsProvider(CREDENTIALS_PROVIDER)
            .build();
    server.enqueue(HttpResponse.of(HttpStatus.OK, MediaType.PLAIN_TEXT_UTF_8, ""));
    Object response =
        call.apply(wrapClient(DynamoDbClient.class, DynamoDbAsyncClient.class, client));
    validateOperationResponse(operation, response);
  }
}
