/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.incubating.AwsIncubatingAttributes.AWS_SECRETSMANAGER_SECRET_ARN;
import static java.util.Collections.singletonList;

import com.amazonaws.services.secretsmanager.AWSSecretsManager;
import com.amazonaws.services.secretsmanager.AWSSecretsManagerClientBuilder;
import com.amazonaws.services.secretsmanager.model.CreateSecretRequest;
import com.amazonaws.services.secretsmanager.model.DescribeSecretRequest;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.testing.internal.armeria.common.HttpResponse;
import io.opentelemetry.testing.internal.armeria.common.HttpStatus;
import io.opentelemetry.testing.internal.armeria.common.MediaType;
import java.util.List;
import org.junit.jupiter.api.Test;

public abstract class AbstractSecretsManagerClientTest extends AbstractBaseAwsClientTest {

  public abstract AWSSecretsManagerClientBuilder configureClient(
      AWSSecretsManagerClientBuilder client);

  @Override
  protected boolean hasRequestId() {
    return false;
  }

  @Test
  public void sendCreateSecretRequestWithMockedResponse() throws Exception {
    AWSSecretsManagerClientBuilder clientBuilder = AWSSecretsManagerClientBuilder.standard();
    AWSSecretsManager client =
        configureClient(clientBuilder)
            .withEndpointConfiguration(endpoint)
            .withCredentials(credentialsProvider)
            .build();

    String body =
        "{"
            + "\"ARN\": \"arn:aws:secretsmanager:us-west-2:123456789012:secret:MyTestDatabaseSecret-a1b2c3\","
            + "\"Name\": \"MyTestDatabaseSecret\","
            + "\"VersionId\": \"EXAMPLE1-90ab-cdef-fedc-ba987SECRET1\""
            + "}";
    server.enqueue(HttpResponse.of(HttpStatus.OK, MediaType.PLAIN_TEXT_UTF_8, body));

    Object response =
        client.createSecret(
            new CreateSecretRequest().withName("secretName").withSecretString("secretValue"));

    List<AttributeAssertion> additionalAttributes =
        singletonList(
            equalTo(
                AWS_SECRETSMANAGER_SECRET_ARN,
                "arn:aws:secretsmanager:us-west-2:123456789012:secret:MyTestDatabaseSecret-a1b2c3"));

    assertRequestWithMockedResponse(
        response, client, "AWSSecretsManager", "CreateSecret", "POST", additionalAttributes);
  }

  @Test
  public void sendDescribeSecretRequestWithMockedResponse() throws Exception {
    AWSSecretsManagerClientBuilder clientBuilder = AWSSecretsManagerClientBuilder.standard();
    AWSSecretsManager client =
        configureClient(clientBuilder)
            .withEndpointConfiguration(endpoint)
            .withCredentials(credentialsProvider)
            .build();

    String body =
        "{"
            + "\"ARN\": \"arn:aws:secretsmanager:us-east-1:123456789012:secret:My-Secret-Id-WzAXar\","
            + "\"Name\": \"My-Secret-Id\","
            + "\"VersionId\": \"EXAMPLE1-90ab-cdef-fedc-ba987SECRET1\""
            + "}";

    server.enqueue(HttpResponse.of(HttpStatus.OK, MediaType.PLAIN_TEXT_UTF_8, body));
    Object response =
        client.describeSecret(new DescribeSecretRequest().withSecretId("My-Secret-Id"));

    List<AttributeAssertion> additionalAttributes =
        singletonList(
            equalTo(
                AWS_SECRETSMANAGER_SECRET_ARN,
                "arn:aws:secretsmanager:us-east-1:123456789012:secret:My-Secret-Id-WzAXar"));

    assertRequestWithMockedResponse(
        response, client, "AWSSecretsManager", "DescribeSecret", "POST", additionalAttributes);
  }
}
