/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awssdk.v1_11;

import static io.opentelemetry.instrumentation.awssdk.v1_11.AwsSdkTestUtil.EXPERIMENTAL_ATTRIBUTES;

import com.amazonaws.services.kinesis.AmazonKinesisClientBuilder;
import io.opentelemetry.instrumentation.awssdk.v1_11.AbstractKinesisClientTest;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

class KinesisClientTest extends AbstractKinesisClientTest {
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
  public AmazonKinesisClientBuilder configureClient(AmazonKinesisClientBuilder clientBuilder) {
    return clientBuilder;
  }
}
