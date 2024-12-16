package io.opentelemetry.instrumentation.awssdk.v2_2;

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
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.google.common.collect.ImmutableMap.of;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.SemanticAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.UrlAttributes.URL_FULL;
import static io.opentelemetry.semconv.incubating.AwsIncubatingAttributes.AWS_REQUEST_ID;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_METHOD;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SERVICE;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SYSTEM;
import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractAws2ClientCoreTest {
  protected abstract InstrumentationExtension getTesting();

  abstract ClientOverrideConfiguration.Builder createOverrideConfigurationBuilder();

  static boolean isSqsAttributeInjectionEnabled() {
    // See io.opentelemetry.instrumentation.awssdk.v2_2.autoconfigure.TracingExecutionInterceptor
    return ConfigPropertiesUtil.getBoolean(
        "otel.instrumentation.aws-sdk.experimental-use-propagator-for-messaging", false);
  }

  static final StaticCredentialsProvider CREDENTIALS_PROVIDER = StaticCredentialsProvider
      .create(AwsBasicCredentials.create("my-access-key", "my-secret-key"));

  static MockWebServerExtension server = new MockWebServerExtension();

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

  void configureSdkClient(SdkClientBuilder builder) {
    builder.overrideConfiguration(createOverrideConfigurationBuilder().build());
  }

  static ImmutableMap<String, AttributeValue> createTableRequestKey = ImmutableMap.of(
      "anotherKey", AttributeValue.builder().s("value").build(),
      "key", AttributeValue.builder().s("value").build());

  static ImmutableMap<String, AttributeValue> getItemRequestKey = ImmutableMap.of(
      "keyOne", AttributeValue.builder().s("value").build(),
      "keyTwo", AttributeValue.builder().s("differentValue").build());

  static ImmutableMap<String, AttributeValue> putItemRequestKey = ImmutableMap.of(
      "key", AttributeValue.builder().s("value").build(),
      "attributeOne", AttributeValue.builder().s("one").build(),
      "attributeTwo", AttributeValue.builder().s("two").build());

  private static Stream<Arguments> provideArguments() {
    return Stream.of(
        Arguments.of("CreateTable",
            (Function<DynamoDbClient, Object>) c -> c.createTable(createTableRequest())),
        Arguments.of("DeleteItem",
            (Function<DynamoDbClient, Object>) c -> c.deleteItem(DeleteItemRequest.builder()
                .tableName("sometable")
                .key(createTableRequestKey)
                .conditionExpression("property in (:one, :two)")
                .build())),
        Arguments.of("DeleteItem",
            (Function<DynamoDbClient, Object>) c -> c.deleteTable(
                DeleteTableRequest.builder().tableName("sometable").build())),
        Arguments.of("GetItem",
            (Function<DynamoDbClient, Object>) c -> c.getItem(
                GetItemRequest.builder()
                    .tableName("sometable")
                    .key(getItemRequestKey)
                    .attributesToGet("propertyOne", "propertyTwo")
                    .build())),
        Arguments.of("PutItem",
            (Function<DynamoDbClient, Object>) c -> c.putItem(
                PutItemRequest.builder()
                    .tableName("sometable")
                    .item(putItemRequestKey)
                    .conditionExpression("attributeOne <> :someVal")
                    .build())),
        Arguments.of("Query",
            (Function<DynamoDbClient, Object>) c -> c.query(
                QueryRequest.builder()
                    .tableName("sometable")
                    .select("ALL_ATTRIBUTES")
                    .keyConditionExpression("attribute = :aValue")
                    .filterExpression("anotherAttribute = :someVal")
                    .limit(10).build())),
        Arguments.of("UpdateItem",
            (Function<DynamoDbClient, Object>) c -> c.updateItem(
                UpdateItemRequest.builder()
                    .tableName("sometable")
                    .key(
                        of("keyOne", AttributeValue.builder().s("value").build(),
                            "keyTwo", AttributeValue.builder().s("differentValue").build()))
                    .conditionExpression("attributeOne <> :someVal")
                    .updateExpression("set attributeOne = :updateValue")
                    .build()))
        );
  }

  @ParameterizedTest
  @MethodSource("provideArguments")
  void testSendDynamoDbRequestWithBuilderMockedResponse(String operation,
      Function<DynamoDbClient, Object> call) throws ExecutionException, InterruptedException {
    DynamoDbClientBuilder builder = DynamoDbClient.builder();
    configureSdkClient(builder);
    DynamoDbClient client = builder
        .endpointOverride(server.httpUri())
        .region(Region.AP_NORTHEAST_1)
        .credentialsProvider(CREDENTIALS_PROVIDER)
        .build();
    server.enqueue(HttpResponse.of(HttpStatus.OK, MediaType.PLAIN_TEXT_UTF_8, ""));
    Object response = call.apply(client);

    if (response instanceof Future) {
      response = ((Future<?>) response).get();
    }

    assertThat(response).isNotNull();
    assertThat(response.getClass().getSimpleName()).startsWith(operation);

    RecordedRequest request = server.takeRequest();
    assertThat(request).isNotNull();
    assertThat(request.request().headers().get("X-Amzn-Trace-Id")).isNotNull();
    assertThat(request.request().headers().get("traceparent")).isNotNull();


    getTesting().waitAndAssertTraces(
        trace -> trace.hasSpansSatisfyingExactly(
            span -> {
              if (operation.equals("CreateTable")) {
                assertCreateTableRequest(span);
              } else if (operation.equals("Query")) {
                assertQueryRequest(span);
              } else {
                assertDynamoDbRequest(span, operation);
              }
            }
        )
    );
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
                        .readCapacityUnits(10l)
                        .writeCapacityUnits(12l)
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
                        .readCapacityUnits(7l)
                        .writeCapacityUnits(8l)
                        .build()
                )
                .build()))
        .provisionedThroughput(
            ProvisionedThroughput.builder()
                .readCapacityUnits(1l)
                .writeCapacityUnits(1l)
                .build()
        )
        .build();
  }

  static SpanDataAssert assertCreateTableRequest(SpanDataAssert span) {
    return span.hasName("DynamoDb.CreateTable")
        .hasKind(SpanKind.CLIENT)
        .hasNoParent()
        .hasAttributesSatisfyingExactly(
            equalTo(SERVER_ADDRESS, "127.0.0.1"),
            equalTo(SERVER_PORT, server.httpPort()),
            equalTo(URL_FULL, server.httpUri().toString()),
            equalTo(HTTP_REQUEST_METHOD, "POST"),
            equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
            equalTo(RPC_SYSTEM, "aws-api"),
            equalTo(RPC_SERVICE, "DynamoDb"),
            equalTo(RPC_METHOD, "CreateTable"),
            equalTo(stringKey("aws.agent"), "java-aws-sdk"),
            equalTo(AWS_REQUEST_ID, "UNKOWN"),
            equalTo(stringKey("aws.table.name"), "sometable"),
            equalTo(DB_SYSTEM, "dynamodb"),
            equalTo(maybeStable(DB_OPERATION), "CreateTable"),
            equalTo(stringKey("aws.dynamodb.global_secondary_indexes"), "[{\"IndexName\":\"globalIndex\",\"KeySchema\":[{\"AttributeName\":\"attribute\"}],\"ProvisionedThroughput\":{\"ReadCapacityUnits\":10,\"WriteCapacityUnits\":12}},{\"IndexName\":\"globalIndexSecondary\",\"KeySchema\":[{\"AttributeName\":\"attributeSecondary\"}],\"ProvisionedThroughput\":{\"ReadCapacityUnits\":7,\"WriteCapacityUnits\":8}}]"),
            equalTo(stringKey("aws.dynamodb.provisioned_throughput.read_capacity_units"), "1"),
            equalTo(stringKey("aws.dynamodb.provisioned_throughput.write_capacity_units"), "1"));
  }

  static SpanDataAssert assertQueryRequest(SpanDataAssert span) {
    return span.hasName("DynamoDb.Query")
        .hasKind(SpanKind.CLIENT)
        .hasNoParent()
        .hasAttributesSatisfyingExactly(
            equalTo(SERVER_ADDRESS, "127.0.0.1"),
            equalTo(SERVER_PORT, server.httpPort()),
            equalTo(URL_FULL, server.httpUri().toString()),
            equalTo(HTTP_REQUEST_METHOD, "POST"),
            equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
            equalTo(RPC_SYSTEM, "aws-api"),
            equalTo(RPC_SERVICE, "DynamoDb"),
            equalTo(RPC_METHOD, "Query"),
            equalTo(stringKey("aws.agent"), "java-aws-sdk"),
            equalTo(AWS_REQUEST_ID, "UNKOWN"),
            equalTo(stringKey("aws.table.name"), "sometable"),
            equalTo(DB_SYSTEM, "dynamodb"),
            equalTo(maybeStable(DB_OPERATION), "Query"),
            equalTo(stringKey("aws.dynamodb.limit"), "10"),
            equalTo(stringKey("aws.dynamodb.select"), "ALL_ATTRIBUTES"));
  }

  static SpanDataAssert assertDynamoDbRequest(SpanDataAssert span, String operation) {
    return span.hasName("DynamoDb." + operation)
        .hasKind(SpanKind.CLIENT)
        .hasNoParent()
        .hasAttributesSatisfyingExactly(
            equalTo(SERVER_ADDRESS, "127.0.0.1"),
            equalTo(SERVER_PORT, server.httpPort()),
            equalTo(URL_FULL, server.httpUri().toString()),
            equalTo(HTTP_REQUEST_METHOD, "POST"),
            equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
            equalTo(RPC_SYSTEM, "aws-api"),
            equalTo(RPC_SERVICE, "DynamoDb"),
            equalTo(RPC_METHOD, operation),
            equalTo(stringKey("aws.agent"), "java-aws-sdk"),
            equalTo(AWS_REQUEST_ID, "UNKOWN"),
            equalTo(stringKey("aws.table.name"), "sometable"),
            equalTo(DB_SYSTEM, "dynamodb"),
            equalTo(maybeStable(DB_OPERATION), operation));
  }
}
