/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.instrumentation.testing.junit.db.DbClientMetricsTestUtil.assertDurationMetric;
import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.DbAttributes.DB_COLLECTION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_BATCH_SIZE;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.AwsIncubatingAttributes.AWS_DYNAMODB_TABLE_NAMES;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemIncubatingValues.DYNAMODB;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.AWS_DYNAMODB;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.BatchGetItemRequest;
import com.amazonaws.services.dynamodbv2.model.BatchWriteItemRequest;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.DeleteRequest;
import com.amazonaws.services.dynamodbv2.model.KeysAndAttributes;
import com.amazonaws.services.dynamodbv2.model.PutRequest;
import com.amazonaws.services.dynamodbv2.model.WriteRequest;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.testing.internal.armeria.common.HttpResponse;
import io.opentelemetry.testing.internal.armeria.common.HttpStatus;
import io.opentelemetry.testing.internal.armeria.common.MediaType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public abstract class AbstractDynamoDbClientTest extends AbstractBaseAwsClientTest {

  public abstract AmazonDynamoDBClientBuilder configureClient(AmazonDynamoDBClientBuilder client);

  @Override
  protected boolean hasRequestId() {
    return false;
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  @Test
  void sendRequestWithMockedResponse() throws ReflectiveOperationException {
    AmazonDynamoDB client = createClient();

    server.enqueue(HttpResponse.of(HttpStatus.OK, MediaType.PLAIN_TEXT_UTF_8, ""));

    List<AttributeAssertion> additionalAttributes =
        new ArrayList<>(
            asList(
                equalTo(
                    maybeStable(DB_SYSTEM), emitStableDatabaseSemconv() ? AWS_DYNAMODB : DYNAMODB),
                equalTo(maybeStable(DB_OPERATION), "CreateTable"),
                equalTo(AWS_DYNAMODB_TABLE_NAMES, singletonList("sometable"))));
    if (emitStableDatabaseSemconv()) {
      additionalAttributes.add(equalTo(DB_COLLECTION_NAME, "sometable"));
    }

    Object response = client.createTable(new CreateTableRequest("sometable", null));
    assertRequestWithMockedResponse(
        response, client, "DynamoDBv2", "CreateTable", "POST", additionalAttributes);

    assertDurationMetric(
        testing(),
        "io.opentelemetry.aws-sdk-1.11",
        DB_SYSTEM_NAME,
        DB_OPERATION_NAME,
        DB_COLLECTION_NAME,
        SERVER_ADDRESS,
        SERVER_PORT);
  }

  // describes the batch cases for the two DynamoDB batch operations (BatchGetItem and
  // BatchWriteItem): the request to send and the expected client span. batch attributes
  // (db.operation.batch.size, BATCH operation name, db.collection.name) are only emitted under
  // stable database semconv; the span and db.operation.name are emitted in both modes
  @SuppressWarnings("deprecation") // using deprecated semconv
  @ParameterizedTest
  @MethodSource("batchScenarios")
  void batchOperation(BatchScenario scenario) throws ReflectiveOperationException {
    AmazonDynamoDB client = createClient();

    server.enqueue(HttpResponse.of(HttpStatus.OK, MediaType.PLAIN_TEXT_UTF_8, "{}"));

    List<AttributeAssertion> additionalAttributes =
        new ArrayList<>(
            asList(
                equalTo(
                    maybeStable(DB_SYSTEM), emitStableDatabaseSemconv() ? AWS_DYNAMODB : DYNAMODB),
                equalTo(
                    maybeStable(DB_OPERATION),
                    emitStableDatabaseSemconv() ? scenario.stableOperation : scenario.awsOperation),
                equalTo(
                    DB_OPERATION_BATCH_SIZE,
                    emitStableDatabaseSemconv() ? scenario.batchSize : null),
                equalTo(
                    DB_COLLECTION_NAME,
                    emitStableDatabaseSemconv() && scenario.hasCollection ? "sometable" : null)));

    Object response = scenario.execute.apply(client);
    assertRequestWithMockedResponse(
        response, client, "DynamoDBv2", scenario.awsOperation, "POST", additionalAttributes);
  }

  private static Stream<BatchScenario> batchScenarios() {
    return Stream.of(
        // an empty batch keeps the raw batch operation name and emits no batch size or
        // collection name
        BatchScenario.builder("getItemEmpty")
            .awsOperation("BatchGetItem")
            .execute(client -> client.batchGetItem(getItemRequest(0)))
            .stableOperation("BatchGetItem")
            .build(),
        // a single-item batch is not a batch, so it uses the singular item operation
        BatchScenario.builder("getItemSingle")
            .awsOperation("BatchGetItem")
            .execute(client -> client.batchGetItem(getItemRequest(1)))
            .stableOperation("GetItem")
            .hasCollection()
            .build(),
        BatchScenario.builder("getItemTwo")
            .awsOperation("BatchGetItem")
            .execute(client -> client.batchGetItem(getItemRequest(2)))
            .stableOperation("BATCH GetItem")
            .batchSize(2)
            .hasCollection()
            .build(),
        BatchScenario.builder("writeItemEmpty")
            .awsOperation("BatchWriteItem")
            .execute(client -> client.batchWriteItem(writeItemRequest(0)))
            .stableOperation("BatchWriteItem")
            .build(),
        BatchScenario.builder("writeItemSingle")
            .awsOperation("BatchWriteItem")
            .execute(client -> client.batchWriteItem(writeItemRequest(1)))
            .stableOperation("WriteItem")
            .hasCollection()
            .build(),
        BatchScenario.builder("writeItemTwo")
            .awsOperation("BatchWriteItem")
            .execute(client -> client.batchWriteItem(writeItemRequest(2)))
            .stableOperation("BATCH WriteItem")
            .batchSize(2)
            .hasCollection()
            .build(),
        // a batch mixing a put and a delete in one table: unlike the SQL/Cassandra matrices where
        // a batch with differing operations collapses to just "BATCH", DynamoDB derives the
        // operation name from the item count alone, so a put+delete write batch still reports the
        // shared "BATCH WriteItem" operation (and the single collection)
        BatchScenario.builder("writeItemMixed")
            .awsOperation("BatchWriteItem")
            .execute(client -> client.batchWriteItem(mixedWriteItemRequest()))
            .stableOperation("BATCH WriteItem")
            .batchSize(2)
            .hasCollection()
            .build());
  }

  private static BatchGetItemRequest getItemRequest(int count) {
    if (count == 0) {
      return new BatchGetItemRequest().withRequestItems(emptyMap());
    }
    List<Map<String, AttributeValue>> keys = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      keys.add(singletonMap("key", new AttributeValue().withS("value" + i)));
    }
    return new BatchGetItemRequest()
        .withRequestItems(singletonMap("sometable", new KeysAndAttributes().withKeys(keys)));
  }

  private static BatchWriteItemRequest writeItemRequest(int count) {
    if (count == 0) {
      return new BatchWriteItemRequest().withRequestItems(emptyMap());
    }
    List<WriteRequest> writes = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      writes.add(writeRequest("value" + i));
    }
    return new BatchWriteItemRequest().withRequestItems(singletonMap("sometable", writes));
  }

  private static WriteRequest writeRequest(String value) {
    return new WriteRequest()
        .withPutRequest(
            new PutRequest().withItem(singletonMap("key", new AttributeValue().withS(value))));
  }

  private static BatchWriteItemRequest mixedWriteItemRequest() {
    List<WriteRequest> writes =
        asList(
            new WriteRequest()
                .withPutRequest(
                    new PutRequest()
                        .withItem(singletonMap("key", new AttributeValue().withS("value")))),
            new WriteRequest()
                .withDeleteRequest(
                    new DeleteRequest()
                        .withKey(singletonMap("key", new AttributeValue().withS("anotherValue")))));
    return new BatchWriteItemRequest().withRequestItems(singletonMap("sometable", writes));
  }

  private static final class BatchScenario {
    final String name;
    final String awsOperation;
    final Function<AmazonDynamoDB, Object> execute;
    final String stableOperation;
    final Long batchSize;
    final boolean hasCollection;

    BatchScenario(Builder builder) {
      this.name = builder.name;
      this.awsOperation = builder.awsOperation;
      this.execute = builder.execute;
      this.stableOperation = builder.stableOperation;
      this.batchSize = builder.batchSize;
      this.hasCollection = builder.hasCollection;
    }

    @Override
    public String toString() {
      // used as the parameterized test display name
      return name;
    }

    static Builder builder(String name) {
      return new Builder(name);
    }

    static final class Builder {
      private final String name;
      private String awsOperation;
      private Function<AmazonDynamoDB, Object> execute;
      private String stableOperation;
      private Long batchSize;
      private boolean hasCollection;

      Builder(String name) {
        this.name = name;
      }

      Builder awsOperation(String awsOperation) {
        this.awsOperation = awsOperation;
        return this;
      }

      Builder execute(Function<AmazonDynamoDB, Object> execute) {
        this.execute = execute;
        return this;
      }

      Builder stableOperation(String stableOperation) {
        this.stableOperation = stableOperation;
        return this;
      }

      Builder batchSize(long batchSize) {
        this.batchSize = batchSize;
        return this;
      }

      Builder hasCollection() {
        this.hasCollection = true;
        return this;
      }

      BatchScenario build() {
        return new BatchScenario(this);
      }
    }
  }

  private AmazonDynamoDB createClient() {
    AmazonDynamoDBClientBuilder clientBuilder = AmazonDynamoDBClientBuilder.standard();
    return configureClient(clientBuilder)
        .withEndpointConfiguration(endpoint)
        .withCredentials(credentialsProvider)
        .build();
  }
}
