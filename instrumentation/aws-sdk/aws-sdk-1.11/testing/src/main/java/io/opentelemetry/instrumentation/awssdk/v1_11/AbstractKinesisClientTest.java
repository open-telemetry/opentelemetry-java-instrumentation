/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static java.util.Collections.singletonList;

import com.amazonaws.services.kinesis.AmazonKinesis;
import com.amazonaws.services.kinesis.AmazonKinesisClientBuilder;
import com.amazonaws.services.kinesis.model.DeleteStreamRequest;
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

public abstract class AbstractKinesisClientTest extends AbstractBaseAwsClientTest {

  public abstract AmazonKinesisClientBuilder configureClient(AmazonKinesisClientBuilder client);

  @Override
  protected boolean hasRequestId() {
    return false;
  }

  @ParameterizedTest
  @MethodSource("provideArguments")
  public void testSendRequestWithMockedResponse(
      String operation, Function<AmazonKinesis, Object> call) throws Exception {
    AmazonKinesisClientBuilder clientBuilder = AmazonKinesisClientBuilder.standard();

    AmazonKinesis client =
        configureClient(clientBuilder)
            .withEndpointConfiguration(endpoint)
            .withCredentials(credentialsProvider)
            .build();

    server.enqueue(HttpResponse.of(HttpStatus.OK, MediaType.PLAIN_TEXT_UTF_8, ""));

    List<AttributeAssertion> additionalAttributes =
        singletonList(equalTo(stringKey("aws.stream.name"), "somestream"));

    Object response = call.apply(client);
    assertRequestWithMockedResponse(
        response, client, "Kinesis", operation, "POST", additionalAttributes);
  }

  private static Stream<Arguments> provideArguments() {
    return Stream.of(
        Arguments.of(
            "DeleteStream",
            (Function<AmazonKinesis, Object>)
                c -> c.deleteStream(new DeleteStreamRequest().withStreamName("somestream"))),
        // Some users may implicitly subclass the request object to mimic a fluent style
        Arguments.of(
            "CustomDeleteStream",
            (Function<AmazonKinesis, Object>)
                c -> c.deleteStream(new CustomDeleteStreamRequest("somestream"))));
  }

  public static class CustomDeleteStreamRequest extends DeleteStreamRequest {
    public CustomDeleteStreamRequest(String streamName) {
      withStreamName(streamName);
    }
  }
}
