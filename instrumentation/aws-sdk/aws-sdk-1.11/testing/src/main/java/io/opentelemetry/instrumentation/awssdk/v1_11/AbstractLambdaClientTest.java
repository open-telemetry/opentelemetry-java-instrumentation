/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static java.util.Collections.singletonList;

import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.AWSLambdaClientBuilder;
import com.amazonaws.services.lambda.model.GetEventSourceMappingRequest;
import com.amazonaws.services.lambda.model.GetFunctionRequest;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.testing.internal.armeria.common.HttpResponse;
import io.opentelemetry.testing.internal.armeria.common.HttpStatus;
import io.opentelemetry.testing.internal.armeria.common.MediaType;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public abstract class AbstractLambdaClientTest extends AbstractBaseAwsClientTest {

  public abstract AWSLambdaClientBuilder configureClient(AWSLambdaClientBuilder client);

  @Override
  protected boolean hasRequestId() {
    return false;
  }

  @ParameterizedTest
  @MethodSource("provideArguments")
  public void testSendRequestWithMockedResponse(
      String operation,
      List<AttributeAssertion> additionalAttributes,
      Function<AWSLambda, Object> call)
      throws Exception {

    AWSLambdaClientBuilder clientBuilder = AWSLambdaClientBuilder.standard();

    AWSLambda client =
        configureClient(clientBuilder)
            .withEndpointConfiguration(endpoint)
            .withCredentials(credentialsProvider)
            .build();

    server.enqueue(HttpResponse.of(HttpStatus.OK, MediaType.PLAIN_TEXT_UTF_8, ""));

    Object response = call.apply(client);
    assertRequestWithMockedResponse(
        response, client, "AWSLambda", operation, "GET", additionalAttributes);
  }

  private static Stream<Arguments> provideArguments() {
    return Stream.of(
        Arguments.of(
            "GetEventSourceMapping",
            singletonList(equalTo(stringKey("aws.lambda.resource_mapping.id"), "uuid")),
            (Function<AWSLambda, Object>)
                c -> c.getEventSourceMapping(new GetEventSourceMappingRequest().withUUID("uuid"))),
        Arguments.of(
            "GetFunction",
            singletonList(equalTo(stringKey("aws.lambda.function.name"), "functionName")),
            (Function<AWSLambda, Object>)
                c -> c.getFunction(new GetFunctionRequest().withFunctionName("functionName"))));
  }
}
