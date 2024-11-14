/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awssdk.v1_11;

import com.amazonaws.services.kinesis.AmazonKinesisClientBuilder;
import io.opentelemetry.instrumentation.awssdk.v1_11.AbstractKinesisClientTest;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

class KinesisClientTest extends AbstractKinesisClientTest {
  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Override
  public AmazonKinesisClientBuilder configureClient(AmazonKinesisClientBuilder clientBuilder) {
    return clientBuilder;
  }
}
