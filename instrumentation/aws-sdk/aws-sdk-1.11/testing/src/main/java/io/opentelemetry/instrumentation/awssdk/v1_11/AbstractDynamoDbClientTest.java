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
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.BatchGetItemRequest;
import com.amazonaws.services.dynamodbv2.model.BatchWriteItemRequest;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.KeysAndAttributes;
import com.amazonaws.services.dynamodbv2.model.PutRequest;
import com.amazonaws.services.dynamodbv2.model.WriteRequest;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.testing.internal.armeria.common.HttpResponse;
import io.opentelemetry.testing.internal.armeria.common.HttpStatus;
import io.opentelemetry.testing.internal.armeria.common.MediaType;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

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

  @SuppressWarnings("deprecation") // using deprecated semconv
  @Test
  void batchGetItemWithMultipleItemsUsesStableBatchAttributes()
      throws ReflectiveOperationException {
    AmazonDynamoDB client = createClient();

    server.enqueue(HttpResponse.of(HttpStatus.OK, MediaType.PLAIN_TEXT_UTF_8, "{}"));

    List<AttributeAssertion> additionalAttributes =
        new ArrayList<>(
            asList(
                equalTo(
                    maybeStable(DB_SYSTEM), emitStableDatabaseSemconv() ? AWS_DYNAMODB : DYNAMODB),
                equalTo(
                    maybeStable(DB_OPERATION),
                    emitStableDatabaseSemconv() ? "BATCH GetItem" : "BatchGetItem"),
                equalTo(
                    DB_OPERATION_BATCH_SIZE, emitStableDatabaseSemconv() ? Long.valueOf(2) : null),
                equalTo(DB_COLLECTION_NAME, emitStableDatabaseSemconv() ? "sometable" : null)));

    Object response =
        client.batchGetItem(
            new BatchGetItemRequest()
                .withRequestItems(
                    singletonMap(
                        "sometable",
                        new KeysAndAttributes()
                            .withKeys(
                                asList(
                                    singletonMap("key", new AttributeValue().withS("value")),
                                    singletonMap(
                                        "key", new AttributeValue().withS("anotherValue")))))));
    assertRequestWithMockedResponse(
        response, client, "DynamoDBv2", "BatchGetItem", "POST", additionalAttributes);

    assertDurationMetric(
        testing(),
        "io.opentelemetry.aws-sdk-1.11",
        DB_SYSTEM_NAME,
        DB_OPERATION_NAME,
        DB_COLLECTION_NAME,
        SERVER_ADDRESS,
        SERVER_PORT);
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  @Test
  void batchGetItemWithSingleItemUsesStableItemOperation() throws ReflectiveOperationException {
    AmazonDynamoDB client = createClient();

    server.enqueue(HttpResponse.of(HttpStatus.OK, MediaType.PLAIN_TEXT_UTF_8, "{}"));

    List<AttributeAssertion> additionalAttributes =
        new ArrayList<>(
            asList(
                equalTo(
                    maybeStable(DB_SYSTEM), emitStableDatabaseSemconv() ? AWS_DYNAMODB : DYNAMODB),
                equalTo(
                    maybeStable(DB_OPERATION),
                    emitStableDatabaseSemconv() ? "GetItem" : "BatchGetItem"),
                equalTo(DB_COLLECTION_NAME, emitStableDatabaseSemconv() ? "sometable" : null)));

    Object response =
        client.batchGetItem(
            new BatchGetItemRequest()
                .withRequestItems(
                    singletonMap(
                        "sometable",
                        new KeysAndAttributes()
                            .withKeys(
                                singletonList(
                                    singletonMap("key", new AttributeValue().withS("value")))))));
    assertRequestWithMockedResponse(
        response, client, "DynamoDBv2", "BatchGetItem", "POST", additionalAttributes);

    assertDurationMetric(
        testing(),
        "io.opentelemetry.aws-sdk-1.11",
        DB_SYSTEM_NAME,
        DB_OPERATION_NAME,
        DB_COLLECTION_NAME,
        SERVER_ADDRESS,
        SERVER_PORT);
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  @Test
  void batchWriteItemWithMultipleItemsUsesStableBatchAttributes()
      throws ReflectiveOperationException {
    AmazonDynamoDB client = createClient();

    server.enqueue(HttpResponse.of(HttpStatus.OK, MediaType.PLAIN_TEXT_UTF_8, "{}"));

    List<AttributeAssertion> additionalAttributes =
        new ArrayList<>(
            asList(
                equalTo(
                    maybeStable(DB_SYSTEM), emitStableDatabaseSemconv() ? AWS_DYNAMODB : DYNAMODB),
                equalTo(
                    maybeStable(DB_OPERATION),
                    emitStableDatabaseSemconv() ? "BATCH WriteItem" : "BatchWriteItem"),
                equalTo(
                    DB_OPERATION_BATCH_SIZE, emitStableDatabaseSemconv() ? Long.valueOf(2) : null),
                equalTo(DB_COLLECTION_NAME, emitStableDatabaseSemconv() ? "sometable" : null)));

    Object response =
        client.batchWriteItem(
            new BatchWriteItemRequest()
                .withRequestItems(
                    singletonMap(
                        "sometable", asList(writeRequest("value"), writeRequest("anotherValue")))));
    assertRequestWithMockedResponse(
        response, client, "DynamoDBv2", "BatchWriteItem", "POST", additionalAttributes);

    assertDurationMetric(
        testing(),
        "io.opentelemetry.aws-sdk-1.11",
        DB_SYSTEM_NAME,
        DB_OPERATION_NAME,
        DB_COLLECTION_NAME,
        SERVER_ADDRESS,
        SERVER_PORT);
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  @Test
  void batchWriteItemWithSingleItemUsesStableItemOperation() throws ReflectiveOperationException {
    AmazonDynamoDB client = createClient();

    server.enqueue(HttpResponse.of(HttpStatus.OK, MediaType.PLAIN_TEXT_UTF_8, "{}"));

    List<AttributeAssertion> additionalAttributes =
        new ArrayList<>(
            asList(
                equalTo(
                    maybeStable(DB_SYSTEM), emitStableDatabaseSemconv() ? AWS_DYNAMODB : DYNAMODB),
                equalTo(
                    maybeStable(DB_OPERATION),
                    emitStableDatabaseSemconv() ? "WriteItem" : "BatchWriteItem"),
                equalTo(DB_COLLECTION_NAME, emitStableDatabaseSemconv() ? "sometable" : null)));

    Object response =
        client.batchWriteItem(
            new BatchWriteItemRequest()
                .withRequestItems(singletonMap("sometable", singletonList(writeRequest("value")))));
    assertRequestWithMockedResponse(
        response, client, "DynamoDBv2", "BatchWriteItem", "POST", additionalAttributes);

    assertDurationMetric(
        testing(),
        "io.opentelemetry.aws-sdk-1.11",
        DB_SYSTEM_NAME,
        DB_OPERATION_NAME,
        DB_COLLECTION_NAME,
        SERVER_ADDRESS,
        SERVER_PORT);
  }

  private static WriteRequest writeRequest(String value) {
    return new WriteRequest()
        .withPutRequest(
            new PutRequest().withItem(singletonMap("key", new AttributeValue().withS(value))));
  }

  private AmazonDynamoDB createClient() {
    AmazonDynamoDBClientBuilder clientBuilder = AmazonDynamoDBClientBuilder.standard();
    return configureClient(clientBuilder)
        .withEndpointConfiguration(endpoint)
        .withCredentials(credentialsProvider)
        .build();
  }
}
