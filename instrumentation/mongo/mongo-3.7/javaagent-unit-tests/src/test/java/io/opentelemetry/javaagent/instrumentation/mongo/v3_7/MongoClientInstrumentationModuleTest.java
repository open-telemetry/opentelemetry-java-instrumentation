/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mongo.v3_7;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import org.junit.jupiter.api.Test;

class MongoClientInstrumentationModuleTest {

  @Test
  void legacyModuleNames() {
    InstrumentationModule module =
        new io.opentelemetry.javaagent.instrumentation.mongo.v3_1
            .MongoClientInstrumentationModule();

    assertThat(module.instrumentationName()).isEqualTo("mongo");
    assertThat(module.instrumentationNames())
        .containsExactly("mongo", "mongo-3.1", "mongo-3.1-core");
  }

  @Test
  void newApiModuleNames() {
    InstrumentationModule module = new MongoClientInstrumentationModule();

    assertThat(module.instrumentationName()).isEqualTo("mongo");
    assertThat(module.instrumentationNames())
        .containsExactly("mongo", "mongo-3.7", "mongo-3.7-core");
  }
}
