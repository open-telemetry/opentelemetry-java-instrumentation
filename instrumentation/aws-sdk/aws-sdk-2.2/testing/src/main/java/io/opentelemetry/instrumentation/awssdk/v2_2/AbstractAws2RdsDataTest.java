/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.api.internal.SemconvExceptionSignal.emitExceptionAsSpanEvents;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.instrumentation.testing.junit.db.DbClientMetricsTestUtil.assertDurationMetric;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_BATCH_SIZE;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_SUMMARY;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_TEXT;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.UrlAttributes.URL_FULL;
import static io.opentelemetry.semconv.incubating.AwsIncubatingAttributes.AWS_REQUEST_ID;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SQL_TABLE;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_METHOD;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SERVICE;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SYSTEM;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.testing.internal.armeria.common.HttpData;
import io.opentelemetry.testing.internal.armeria.common.HttpResponse;
import io.opentelemetry.testing.internal.armeria.common.HttpStatus;
import io.opentelemetry.testing.internal.armeria.common.MediaType;
import io.opentelemetry.testing.internal.armeria.common.ResponseHeaders;
import io.opentelemetry.testing.internal.armeria.testing.junit5.server.mock.MockWebServerExtension;
import io.opentelemetry.testing.internal.armeria.testing.junit5.server.mock.RecordedRequest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.rdsdata.RdsDataAsyncClient;
import software.amazon.awssdk.services.rdsdata.RdsDataClient;
import software.amazon.awssdk.services.rdsdata.model.BadRequestException;
import software.amazon.awssdk.services.rdsdata.model.BatchExecuteStatementRequest;
import software.amazon.awssdk.services.rdsdata.model.BeginTransactionRequest;
import software.amazon.awssdk.services.rdsdata.model.ExecuteStatementRequest;
import software.amazon.awssdk.services.rdsdata.model.Field;
import software.amazon.awssdk.services.rdsdata.model.SqlParameter;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractAws2RdsDataTest {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.aws-sdk-2.2";
  private static final String AWS_SERVICE = "RdsData";
  private static final String DATABASE = "customers_db";
  private static final String RESOURCE_ARN =
      "arn:aws:rds:us-east-1:123456789012:cluster:test-cluster";
  private static final String SECRET_ARN =
      "arn:aws:secretsmanager:us-east-1:123456789012:secret:test-secret";
  private static final String REQUEST_ID = "7a62c49f-347e-4fc4-9331-6e8e7a96aa73";

  private static final StaticCredentialsProvider CREDENTIALS_PROVIDER =
      StaticCredentialsProvider.create(
          AwsBasicCredentials.create("my-access-key", "my-secret-key"));

  @RegisterExtension
  private static final MockWebServerExtension server = new MockWebServerExtension();

  @RegisterExtension final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private RdsDataClient client;
  private RdsDataAsyncClient asyncClient;

  protected abstract InstrumentationExtension getTesting();

  protected abstract ClientOverrideConfiguration.Builder createOverrideConfigurationBuilder();

  @BeforeAll
  void setUpClients() {
    client =
        RdsDataClient.builder()
            .overrideConfiguration(createOverrideConfigurationBuilder().build())
            .endpointOverride(server.httpUri())
            .region(Region.US_EAST_1)
            .credentialsProvider(CREDENTIALS_PROVIDER)
            .build();
    cleanup.deferAfterAll(client);

    asyncClient =
        RdsDataAsyncClient.builder()
            .overrideConfiguration(createOverrideConfigurationBuilder().build())
            .endpointOverride(server.httpUri())
            .region(Region.US_EAST_1)
            .credentialsProvider(CREDENTIALS_PROVIDER)
            .build();
    cleanup.deferAfterAll(asyncClient);
  }

  @Test
  void executeStatementSanitizesLiterals() {
    String sql = "SELECT * FROM customers WHERE email = 'person@example.com'";
    enqueueSuccess("{}");

    client.executeStatement(executeStatementRequest(sql));

    assertRequestPath("/Execute");
    assertSqlSpan(
        "ExecuteStatement",
        "/Execute",
        "SELECT * FROM customers WHERE email = ?",
        "SELECT",
        "customers",
        "SELECT * FROM customers WHERE email = ?",
        "SELECT customers",
        null);
    assertSqlDurationMetric();
  }

  @Test
  void executeStatementAsyncRetainsParameterizedQuery() {
    String sql = "SELECT * FROM customers WHERE email = :email";
    SqlParameter parameter =
        SqlParameter.builder()
            .name("email")
            .value(Field.builder().stringValue("person@example.com").build())
            .build();
    enqueueSuccess("{}");

    asyncClient
        .executeStatement(executeStatementRequest(sql).toBuilder().parameters(parameter).build())
        .join();

    assertRequestPath("/Execute");
    assertSqlSpan(
        "ExecuteStatement", "/Execute", sql, "SELECT", "customers", sql, "SELECT customers", null);
    assertSqlDurationMetric();
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2})
  void batchExecuteStatementModelsParameterSetCount(int parameterSetCount) {
    String sql = "INSERT INTO customers (id) VALUES (?)";
    List<List<SqlParameter>> parameterSets = new ArrayList<>();
    for (int i = 0; i < parameterSetCount; i++) {
      parameterSets.add(
          singletonList(
              SqlParameter.builder()
                  .name("id")
                  .value(Field.builder().longValue((long) i).build())
                  .build()));
    }
    BatchExecuteStatementRequest request =
        BatchExecuteStatementRequest.builder()
            .resourceArn(RESOURCE_ARN)
            .secretArn(SECRET_ARN)
            .database(DATABASE)
            .sql(sql)
            .parameterSets(parameterSets)
            .build();
    enqueueSuccess("{}");

    client.batchExecuteStatement(request);

    assertRequestPath("/BatchExecute");
    boolean isBatch = parameterSetCount != 1;
    assertSqlSpan(
        "BatchExecuteStatement",
        "/BatchExecute",
        sql,
        "INSERT",
        "customers",
        sql,
        isBatch ? "BATCH INSERT customers" : "INSERT customers",
        isBatch ? (long) parameterSetCount : null);
    assertSqlDurationMetric();
  }

  @Test
  void errorUsesAwsExceptionType() {
    String sql = "SELECT * FROM missing_customers";
    enqueue(
        HttpStatus.BAD_REQUEST,
        "{\"__type\":\"BadRequestException\",\"message\":\"mock failure\"}");

    Throwable error = catchThrowable(() -> client.executeStatement(executeStatementRequest(sql)));

    assertThat(error).isInstanceOf(BadRequestException.class);
    assertRequestPath("/Execute");
    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> {
                      List<AttributeAssertion> attributes =
                          commonAttributes("ExecuteStatement", "/Execute", null, null);
                      addDatabaseAttributes(
                          attributes,
                          sql,
                          "SELECT",
                          "missing_customers",
                          sql,
                          "SELECT missing_customers",
                          null);
                      if (emitStableDatabaseSemconv()) {
                        attributes.add(equalTo(ERROR_TYPE, BadRequestException.class.getName()));
                      }
                      span.hasName(
                              emitStableDatabaseSemconv()
                                  ? "SELECT missing_customers"
                                  : AWS_SERVICE + ".ExecuteStatement")
                          .hasKind(SpanKind.CLIENT)
                          .hasNoParent()
                          .hasStatus(StatusData.error())
                          .hasException(emitExceptionAsSpanEvents() ? error : null)
                          .hasAttributesSatisfyingExactly(attributes);
                    }));
  }

  @Test
  void transactionOperationRemainsGeneric() {
    enqueueSuccess("{\"transactionId\":\"test-transaction-id\"}");

    client.beginTransaction(
        BeginTransactionRequest.builder()
            .resourceArn(RESOURCE_ARN)
            .secretArn(SECRET_ARN)
            .database(DATABASE)
            .build());

    assertRequestPath("/BeginTransaction");
    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName(AWS_SERVICE + ".BeginTransaction")
                            .hasKind(SpanKind.CLIENT)
                            .hasNoParent()
                            .hasAttributesSatisfyingExactly(
                                commonAttributes(
                                    "BeginTransaction", "/BeginTransaction", 200, REQUEST_ID))));
  }

  private static ExecuteStatementRequest executeStatementRequest(String sql) {
    return ExecuteStatementRequest.builder()
        .resourceArn(RESOURCE_ARN)
        .secretArn(SECRET_ARN)
        .database(DATABASE)
        .sql(sql)
        .build();
  }

  private void assertSqlSpan(
      String operation,
      String path,
      String legacyStatement,
      String legacyOperation,
      String legacyTable,
      String stableQueryText,
      String stableQuerySummary,
      Long batchSize) {
    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> {
                      List<AttributeAssertion> attributes =
                          commonAttributes(operation, path, 200, REQUEST_ID);
                      addDatabaseAttributes(
                          attributes,
                          legacyStatement,
                          legacyOperation,
                          legacyTable,
                          stableQueryText,
                          stableQuerySummary,
                          batchSize);
                      span.hasName(
                              emitStableDatabaseSemconv()
                                  ? stableQuerySummary
                                  : AWS_SERVICE + "." + operation)
                          .hasKind(SpanKind.CLIENT)
                          .hasNoParent()
                          .hasAttributesSatisfyingExactly(attributes);
                    }));
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  private static void addDatabaseAttributes(
      List<AttributeAssertion> attributes,
      String legacyStatement,
      String legacyOperation,
      String legacyTable,
      String stableQueryText,
      String stableQuerySummary,
      Long batchSize) {
    if (emitStableDatabaseSemconv()) {
      attributes.add(equalTo(DB_SYSTEM_NAME, "other_sql"));
      attributes.add(equalTo(DB_NAMESPACE, DATABASE));
      attributes.add(equalTo(DB_QUERY_TEXT, stableQueryText));
      attributes.add(equalTo(DB_QUERY_SUMMARY, stableQuerySummary));
      if (batchSize != null) {
        attributes.add(equalTo(DB_OPERATION_BATCH_SIZE, batchSize));
      }
    } else {
      attributes.add(equalTo(DB_SYSTEM, "other_sql"));
      attributes.add(equalTo(DB_NAME, DATABASE));
      attributes.add(equalTo(DB_STATEMENT, legacyStatement));
      attributes.add(equalTo(DB_OPERATION, legacyOperation));
      attributes.add(equalTo(DB_SQL_TABLE, legacyTable));
    }
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  private static List<AttributeAssertion> commonAttributes(
      String operation, String path, Integer statusCode, String requestId) {
    List<AttributeAssertion> attributes =
        new ArrayList<>(
            asList(
                equalTo(SERVER_ADDRESS, "127.0.0.1"),
                equalTo(SERVER_PORT, server.httpPort()),
                equalTo(URL_FULL, server.httpUri() + path),
                equalTo(HTTP_REQUEST_METHOD, "POST"),
                equalTo(RPC_SYSTEM, "aws-api"),
                equalTo(RPC_SERVICE, AWS_SERVICE),
                equalTo(RPC_METHOD, operation),
                equalTo(stringKey("aws.agent"), "java-aws-sdk")));
    if (statusCode != null) {
      attributes.add(equalTo(HTTP_RESPONSE_STATUS_CODE, statusCode));
    }
    if (requestId != null) {
      attributes.add(equalTo(AWS_REQUEST_ID, requestId));
    }
    return attributes;
  }

  private void assertSqlDurationMetric() {
    assertDurationMetric(
        getTesting(), INSTRUMENTATION_NAME, DB_SYSTEM_NAME, DB_NAMESPACE, DB_QUERY_SUMMARY);
  }

  private static void enqueueSuccess(String body) {
    enqueue(HttpStatus.OK, body);
  }

  private static void enqueue(HttpStatus status, String body) {
    ResponseHeaders headers =
        ResponseHeaders.builder(status)
            .contentType(MediaType.JSON_UTF_8)
            .add("x-amzn-RequestId", REQUEST_ID)
            .build();
    server.enqueue(HttpResponse.of(headers, HttpData.of(UTF_8, body)));
  }

  private static void assertRequestPath(String path) {
    RecordedRequest request = server.takeRequest();
    assertThat(request.request().path()).isEqualTo(path);
  }
}
