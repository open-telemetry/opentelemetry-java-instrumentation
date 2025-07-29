/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.mongo.v3_1;

import com.mongodb.MongoClientOptions;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

class MongoClientTest extends AbstractMongo31ClientTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Override
  protected void configureMongoClientOptions(MongoClientOptions.Builder options) {
    options.addCommandListener(
        MongoTelemetry.create(testing().getOpenTelemetry()).newCommandListener());
  }

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }
}
