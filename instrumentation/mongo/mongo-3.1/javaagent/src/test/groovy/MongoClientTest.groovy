/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import com.mongodb.MongoClientOptions
import io.opentelemetry.instrumentation.mongo.v3_1.AbstractMongo31ClientTest
import io.opentelemetry.instrumentation.test.AgentTestTrait

class MongoClientTest extends AbstractMongo31ClientTest implements AgentTestTrait {
  @Override
  void configureMongoClientOptions(MongoClientOptions.Builder options) {
  }
}
