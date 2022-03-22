/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.mongo.v3_1

import com.mongodb.MongoClientOptions
import io.opentelemetry.instrumentation.test.LibraryTestTrait

class MongoClientTest extends AbstractMongo31ClientTest implements LibraryTestTrait {
  @Override
  void configureMongoClientOptions(MongoClientOptions.Builder options) {
    options.addCommandListener(MongoTelemetry.create(openTelemetry).newCommandListener())
  }
}
