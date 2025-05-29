/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awssdk.v2_2;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.contrib.awsxray.propagator.AwsXrayPropagator;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

class AwsXrayPropagatorTest {
  private static final StaticCredentialsProvider CREDENTIALS_PROVIDER =
      StaticCredentialsProvider.create(
          AwsBasicCredentials.create("my-access-key", "my-secret-key"));

  @Test
  void testAgentShadesAwsXrayPropagator() {
    // first build a SqsClient to trigger instrumentation
    SqsClient.builder()
        .region(Region.AP_NORTHEAST_1)
        .credentialsProvider(CREDENTIALS_PROVIDER)
        .build();
    // verify that AwsXrayPropagator is usable, if agent has not shaded AwsXrayPropagator this will
    // fail with NoSuchMethodError
    AwsXrayPropagator.getInstance()
        .extract(
            Context.root(),
            Collections.singletonMap(
                "X-Amzn-Trace-Id",
                "Root=1-35a77be2-beae321878f706079d392ac3;Parent=df79f9d51134dc0b;Sampled=1"),
            StringMapGetter.INSTANCE);
  }

  private enum StringMapGetter implements TextMapGetter<Map<String, String>> {
    INSTANCE;

    @Override
    public Iterable<String> keys(Map<String, String> map) {
      return map.keySet();
    }

    @Override
    public String get(Map<String, String> map, String s) {
      return map.get(s);
    }
  }
}
