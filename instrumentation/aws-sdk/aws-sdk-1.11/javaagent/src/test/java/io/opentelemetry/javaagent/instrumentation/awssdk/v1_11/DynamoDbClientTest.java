/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awssdk.v1_11;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import io.opentelemetry.instrumentation.awssdk.v1_11.AbstractDynamoDbClientTest;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

class DynamoDbClientTest extends AbstractDynamoDbClientTest {
  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Override
  public AmazonDynamoDBClientBuilder configureClient(AmazonDynamoDBClientBuilder clientBuilder) {
    return clientBuilder;
  }
}
