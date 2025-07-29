/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mongo.v3_1;

import com.mongodb.MongoClientOptions;
import io.opentelemetry.instrumentation.mongo.v3_1.AbstractMongo31ClientTest;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

class MongoClientTest extends AbstractMongo31ClientTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Override
  protected void configureMongoClientOptions(MongoClientOptions.Builder options) {}

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }
}
