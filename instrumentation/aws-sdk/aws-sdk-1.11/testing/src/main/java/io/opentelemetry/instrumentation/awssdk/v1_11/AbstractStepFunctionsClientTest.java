/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.incubating.AwsIncubatingAttributes.AWS_STEP_FUNCTIONS_ACTIVITY_ARN;
import static io.opentelemetry.semconv.incubating.AwsIncubatingAttributes.AWS_STEP_FUNCTIONS_STATE_MACHINE_ARN;
import static java.util.Collections.singletonList;

import com.amazonaws.services.stepfunctions.AWSStepFunctions;
import com.amazonaws.services.stepfunctions.AWSStepFunctionsClientBuilder;
import com.amazonaws.services.stepfunctions.model.DescribeActivityRequest;
import com.amazonaws.services.stepfunctions.model.DescribeStateMachineRequest;
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

public abstract class AbstractStepFunctionsClientTest extends AbstractBaseAwsClientTest {

  public abstract AWSStepFunctionsClientBuilder configureClient(
      AWSStepFunctionsClientBuilder client);

  @Override
  protected boolean hasRequestId() {
    return false;
  }

  @ParameterizedTest
  @MethodSource("provideArguments")
  public void testSendRequestWithMockedResponse(
      String operation,
      List<AttributeAssertion> additionalAttributes,
      Function<AWSStepFunctions, Object> call)
      throws Exception {

    AWSStepFunctionsClientBuilder clientBuilder = AWSStepFunctionsClientBuilder.standard();

    AWSStepFunctions client =
        configureClient(clientBuilder)
            .withEndpointConfiguration(endpoint)
            .withCredentials(credentialsProvider)
            .build();

    server.enqueue(HttpResponse.of(HttpStatus.OK, MediaType.PLAIN_TEXT_UTF_8, ""));

    Object response = call.apply(client);
    assertRequestWithMockedResponse(
        response, client, "AWSStepFunctions", operation, "POST", additionalAttributes);
  }

  private static Stream<Arguments> provideArguments() {
    return Stream.of(
        Arguments.of(
            "DescribeStateMachine",
            singletonList(equalTo(AWS_STEP_FUNCTIONS_STATE_MACHINE_ARN, "stateMachineArn")),
            (Function<AWSStepFunctions, Object>)
                c ->
                    c.describeStateMachine(
                        new DescribeStateMachineRequest().withStateMachineArn("stateMachineArn"))),
        Arguments.of(
            "DescribeActivity",
            singletonList(equalTo(AWS_STEP_FUNCTIONS_ACTIVITY_ARN, "activityArn")),
            (Function<AWSStepFunctions, Object>)
                c ->
                    c.describeActivity(
                        new DescribeActivityRequest().withActivityArn("activityArn"))));
  }
}
