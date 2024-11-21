/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import io.opentelemetry.testing.internal.armeria.common.HttpResponse;
import io.opentelemetry.testing.internal.armeria.common.HttpStatus;
import io.opentelemetry.testing.internal.armeria.common.MediaType;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static io.opentelemetry.instrumentation.awssdk.v1_11.AttributeKeyPair.createStringArrayKeyPair;
import static io.opentelemetry.instrumentation.awssdk.v1_11.AttributeKeyPair.createStringKeyPair;

public abstract class AbstractDynamoDbClientTest extends AbstractBaseAwsClientTest {

  public abstract AmazonDynamoDBClientBuilder configureClient(AmazonDynamoDBClientBuilder client);

  @Override
  protected boolean hasRequestId() {
    return false;
  }

  @Test
  public void sendRequestWithMockedResponse() throws Exception {
    AmazonDynamoDBClientBuilder clientBuilder = AmazonDynamoDBClientBuilder.standard();
    AmazonDynamoDB client =
        configureClient(clientBuilder)
            .withEndpointConfiguration(endpoint)
            .withCredentials(credentialsProvider)
            .build();

    server.enqueue(HttpResponse.of(HttpStatus.OK, MediaType.PLAIN_TEXT_UTF_8, ""));

    List<String> tableList = Collections.singletonList("sometable");

    List<AttributeKeyPair<?>> additionalAttributes = Arrays.asList(
        createStringKeyPair("aws.table.name", "sometable"),
        createStringKeyPair("db.system", "dynamodb"),
        createStringArrayKeyPair("aws.dynamodb.table_names", tableList)
    );

    Object response = client.createTable(new CreateTableRequest("sometable", null));
    assertRequestWithMockedResponse(
        response,
        client,
        "DynamoDBv2",
        "CreateTable",
        "POST",
        additionalAttributes);
  }
}
