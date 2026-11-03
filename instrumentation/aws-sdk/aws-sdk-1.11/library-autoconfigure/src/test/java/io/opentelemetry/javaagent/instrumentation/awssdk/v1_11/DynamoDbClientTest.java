/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awssdk.v1_11;

import static io.opentelemetry.instrumentation.awssdk.v1_11.AwsSdkTestUtil.EXPERIMENTAL_ATTRIBUTES;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import io.opentelemetry.instrumentation.awssdk.v1_11.AbstractDynamoDbClientTest;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

class DynamoDbClientTest extends AbstractDynamoDbClientTest {
  @RegisterExtension
  private static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Override
  protected boolean captureExperimentalSpanAttributes() {
    return EXPERIMENTAL_ATTRIBUTES;
  }

  @Override
  public AmazonDynamoDBClientBuilder configureClient(AmazonDynamoDBClientBuilder client) {
    return client;
  }
}
