/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mongo.v3_1;

import static io.opentelemetry.instrumentation.testing.util.TestLatestDeps.testLatestDeps;

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

  @Override
  protected boolean supportsNetworkPeer() {
    // the mongo-3.7 instrumentation captures the peer, and it only applies from driver 3.11, which
    // is used when testing the latest dependencies
    return testLatestDeps();
  }
}
