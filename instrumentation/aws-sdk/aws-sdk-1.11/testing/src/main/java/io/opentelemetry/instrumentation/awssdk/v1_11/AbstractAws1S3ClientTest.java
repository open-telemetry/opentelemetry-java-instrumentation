/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.google.common.collect.ImmutableMap;
import io.opentelemetry.testing.internal.armeria.common.HttpResponse;
import io.opentelemetry.testing.internal.armeria.common.HttpStatus;
import io.opentelemetry.testing.internal.armeria.common.MediaType;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public abstract class AbstractAws1S3ClientTest extends AbstractAws1BaseClientTest {

  public abstract AmazonS3ClientBuilder configureClient(AmazonS3ClientBuilder client);

  @ParameterizedTest
  @MethodSource("provideArguments")
  public void testSendRequestWithMockedResponse(
      String operation,
      String method,
      Function<AmazonS3, Object> call,
      Map<String, String> additionalAttributes)
      throws Exception {

    AmazonS3ClientBuilder clientBuilder =
        AmazonS3ClientBuilder.standard().withPathStyleAccessEnabled(true);
    AmazonS3 client =
        configureClient(clientBuilder)
            .withEndpointConfiguration(endpoint)
            .withCredentials(credentialsProvider)
            .build();

    server.enqueue(HttpResponse.of(HttpStatus.OK, MediaType.PLAIN_TEXT_UTF_8, ""));

    Object response = call.apply(client);
    requestWithMockedResponse(response, client, "S3", operation, method, additionalAttributes);
  }

  private static Stream<Arguments> provideArguments() {
    return Stream.of(
        Arguments.of(
            "CreateBucket",
            "PUT",
            (Function<AmazonS3, Object>) c -> c.createBucket("testbucket"),
            ImmutableMap.of("aws.bucket.name", "testbucket")),
        Arguments.of(
            "GetObject",
            "GET",
            (Function<AmazonS3, Object>) c -> c.getObject("someBucket", "someKey"),
            ImmutableMap.of("aws.bucket.name", "someBucket")));
  }
}
