/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.testing.junit.db.DbClientMetricsTestUtil.assertDurationMetric;
import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.AwsIncubatingAttributes.AWS_DYNAMODB_TABLE_NAMES;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.AWS_DYNAMODB;
import static java.util.Collections.singletonList;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import io.opentelemetry.testing.internal.armeria.common.HttpResponse;
import io.opentelemetry.testing.internal.armeria.common.HttpStatus;
import io.opentelemetry.testing.internal.armeria.common.MediaType;
import java.util.Arrays;
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
  public void sendRequestWithMockedResponse() throws Exception {
    AmazonDynamoDBClientBuilder clientBuilder = AmazonDynamoDBClientBuilder.standard();
    AmazonDynamoDB client =
        configureClient(clientBuilder)
            .withEndpointConfiguration(endpoint)
            .withCredentials(credentialsProvider)
            .build();

    server.enqueue(HttpResponse.of(HttpStatus.OK, MediaType.PLAIN_TEXT_UTF_8, ""));

    List<AttributeAssertion> additionalAttributes =
        Arrays.asList(
            equalTo(stringKey("aws.table.name"), "sometable"),
            equalTo(
                maybeStable(DB_SYSTEM),
                SemconvStability.emitStableDatabaseSemconv()
                    ? AWS_DYNAMODB
                    : DbIncubatingAttributes.DbSystemIncubatingValues.DYNAMODB),
            equalTo(maybeStable(DB_OPERATION), "CreateTable"),
            equalTo(AWS_DYNAMODB_TABLE_NAMES, singletonList("sometable")));

    Object response = client.createTable(new CreateTableRequest("sometable", null));
    assertRequestWithMockedResponse(
        response, client, "DynamoDBv2", "CreateTable", "POST", additionalAttributes);

    assertDurationMetric(
        testing(),
        "io.opentelemetry.aws-sdk-1.11",
        DB_SYSTEM_NAME,
        DB_OPERATION_NAME,
        SERVER_ADDRESS,
        SERVER_PORT);
  }
}
