/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mongo.v3_7;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MongoInstrumentationModuleTest {

  @Test
  void modulesHaveDistinctOrderedNames() {
    io.opentelemetry.javaagent.instrumentation.mongo.v3_1.MongoClientInstrumentationModule
        mongo31 =
            new io.opentelemetry.javaagent.instrumentation.mongo.v3_1
                .MongoClientInstrumentationModule();
    MongoClientInstrumentationModule mongo37 = new MongoClientInstrumentationModule();

    assertThat(mongo31.instrumentationNames())
        .containsExactly("mongo", "mongo-3.1", "mongo-3.1-core");
    assertThat(mongo31.instrumentationName()).isEqualTo("mongo");
    assertThat(mongo37.instrumentationNames())
        .containsExactly("mongo", "mongo-3.7", "mongo-3.7-core");
    assertThat(mongo37.instrumentationName()).isEqualTo("mongo");
  }
}
