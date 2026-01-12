/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.incubating.AwsIncubatingAttributes.AWS_LAMBDA_RESOURCE_MAPPING_ID;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.AWSLambdaClientBuilder;
import com.amazonaws.services.lambda.model.CreateEventSourceMappingRequest;
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
  private static final String lambdaCreateEventSourceMappingResponseBody =
      "{"
          + "\"UUID\": \"e31def54-5e5d-4c1b-8e0f-bf1b11c137b7\","
          + "\"BatchSize\": 10,"
          + "\"MaximumBatchingWindowInSeconds\": 0,"
          + "\"EventSourceArn\": \"arn:aws:sqs:us-west-2:123456789012:MyTestQueue.fifo\","
          + "\"FunctionArn\": \"arn:aws:lambda:us-west-2:123456789012:function:myFn01-Temp\","
          + "\"LastModified\": \"1.754882043E9\","
          + "\"State\": \"Creating\","
          + "\"StateTransitionReason\": \"USER_INITIATED\","
          + "\"FunctionResponseTypes\": [],"
          + "\"EventSourceMappingArn\": \"arn:aws:lambda:us-west-2:123456789012:event-source-mapping:e31def54-5e5d-4c1b-8e0f-bf1b11c137b7\""
          + "}";

  private static final String lambdaGetEventSourceMappingResponseBody =
      "{"
          + "\"UUID\": \"e31def54-5e5d-4c1b-8e0f-bf1b11c138c8\","
          + "\"BatchSize\": 10,"
          + "\"MaximumBatchingWindowInSeconds\": 0,"
          + "\"EventSourceArn\": \"arn:aws:sqs:us-west-2:123456789012:MyTestQueue.fifo\","
          + "\"FunctionArn\": \"arn:aws:lambda:us-west-2:123456789012:function:myFn01-Temp\","
          + "\"LastModified\": \"1.755054843E9\","
          + "\"State\": \"Enabled\","
          + "\"StateTransitionReason\": \"USER_INITIATED\","
          + "\"FunctionResponseTypes\": [],"
          + "\"EventSourceMappingArn\": \"arn:aws:lambda:us-west-2:123456789012:event-source-mapping:e31def54-5e5d-4c1b-8e0f-bf1b11c138c8\""
          + "}";

  private static final String lambdaGetFunctionResponseBody =
      "{"
          + "\"Configuration\": {"
          + "   \"FunctionName\": \"lambda-function-name-foo\","
          + "   \"FunctionArn\": \"arn:aws:lambda:us-west-2:123456789012:function:lambda-function-name-foo\","
          + "   \"Runtime\": \"nodejs22.x\","
          + "   \"Role\": \"arn:aws:iam::123456789012:role/service-role/Fn-role-pr7kt0bf\","
          + "   \"Handler\": \"index.handler\","
          + "   \"CodeSize\": 295,"
          + "   \"Description\": \"\","
          + "   \"Timeout\": 3,"
          + "   \"MemorySize\": 128,"
          + "   \"LastModified\": \"1.743573094E9\","
          + "   \"CodeSha256\": \"q8E7Nexf5xxhKT9/d4bGpAYOXJYFAUjJ0UDj8OivK8E=\","
          + "   \"Version\": \"$LATEST\","
          + "   \"Environment\": {"
          + "     \"Variables\": {"
          + "         \"AWS_LAMBDA_EXEC_WRAPPER\": \"/opt/otel-instrument\""
          + "}"
          + "   },"
          + "   \"TracingConfig\": {"
          + "       \"Mode\": \"PassThrough\""
          + "   },"
          + "   \"RevisionId\": \"955247dc-c724-4653-8f8b-f702c6f9c389\","
          + "   \"Layers\": ["
          + "     {"
          + "       \"Arn\": \"arn:aws:lambda:us-west-2:615299751070:layer:AWSOpenTelemetryDistroJs:6\","
          + "       \"CodeSize\": 14455992"
          + "     }"
          + "   ],"
          + "   \"State\": \"Active\","
          + "   \"LastUpdateStatus\": \"Successful\","
          + "   \"PackageType\": \"Zip\","
          + "   \"Architectures\": ["
          + "     \"x86_64\""
          + "   ],"
          + "   \"EphemeralStorage\": {"
          + "     \"Size\": 512"
          + "   },"
          + "  \"SnapStart\": {"
          + "     \"ApplyOn\": \"None\","
          + "     \"OptimizationStatus\": \"Off\""
          + "   },"
          + "   \"RuntimeVersionConfig\": {"
          + "       \"RuntimeVersionArn\": \"arn:aws:lambda:us-west-2::runtime:fd2e05b324f99edd3c6e17800b2535deb79bcce74b7506d595a94870b3d9bd2e\""
          + "   },"
          + "   \"LoggingConfig\": {"
          + "       \"LogFormat\": \"Text\","
          + "       \"LogGroup\": \"/aws/lambda/mlambda-function-name-foo\""
          + "   }"
          + " },"
          + " \"Code\": {"
          + "     \"RepositoryType\": \"S3\","
          + "     \"Location\": \"https://awslambda-us-west-2-tasks.s3.us-west-2.amazonaws.com/snapshots/123456789012/lambda-function-name-foo-47425b12\""
          + " }"
          + "}";

  public abstract AWSLambdaClientBuilder configureClient(AWSLambdaClientBuilder client);

  @Override
  protected boolean hasRequestId() {
    return false;
  }

  private static Stream<Arguments> provideArguments() {
    return Stream.of(
        Arguments.of(
            "CreateEventSourceMapping",
            "POST",
            lambdaCreateEventSourceMappingResponseBody,
            asList(
                equalTo(stringKey("aws.lambda.function.name"), "myFn01-Temp"),
                equalTo(AWS_LAMBDA_RESOURCE_MAPPING_ID, "e31def54-5e5d-4c1b-8e0f-bf1b11c137b7")),
            (Function<AWSLambda, Object>)
                c ->
                    c.createEventSourceMapping(
                        new CreateEventSourceMappingRequest()
                            .withFunctionName("myFn01-Temp")
                            .withEventSourceArn(
                                "arn:aws:sqs:us-west-2:123456789012:MyTestQueue.fifo")
                            .withEnabled(true)
                            .withBatchSize(10))),
        Arguments.of(
            "GetEventSourceMapping",
            "GET",
            lambdaGetEventSourceMappingResponseBody,
            singletonList(
                equalTo(AWS_LAMBDA_RESOURCE_MAPPING_ID, "e31def54-5e5d-4c1b-8e0f-bf1b11c138c8")),
            (Function<AWSLambda, Object>)
                c ->
                    c.getEventSourceMapping(
                        new GetEventSourceMappingRequest()
                            .withUUID("e31def54-5e5d-4c1b-8e0f-bf1b11c138c8"))),
        Arguments.of(
            "GetFunction",
            "GET",
            lambdaGetFunctionResponseBody,
            asList(
                equalTo(stringKey("aws.lambda.function.name"), "lambda-function-name-foo"),
                equalTo(
                    stringKey("aws.lambda.function.arn"),
                    "arn:aws:lambda:us-west-2:123456789012:function:lambda-function-name-foo")),
            (Function<AWSLambda, Object>)
                c ->
                    c.getFunction(
                        new GetFunctionRequest().withFunctionName("lambda-function-name-foo"))));
  }

  @ParameterizedTest
  @MethodSource("provideArguments")
  void testSendRequestWithMockedResponse(
      String operation,
      String method,
      String responseBody,
      List<AttributeAssertion> additionalAttributes,
      Function<AWSLambda, Object> call)
      throws Exception {
    AWSLambdaClientBuilder clientBuilder = AWSLambdaClientBuilder.standard();
    AWSLambda client =
        configureClient(clientBuilder)
            .withEndpointConfiguration(endpoint)
            .withCredentials(credentialsProvider)
            .build();

    server.enqueue(HttpResponse.of(HttpStatus.OK, MediaType.PLAIN_TEXT_UTF_8, responseBody));
    Object response = call.apply(client);
    assertRequestWithMockedResponse(
        response, client, "AWSLambda", operation, method, additionalAttributes);
  }
}
