/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.spring.smoketest;

import io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.mongo.MongoClientInstrumentationAutoConfiguration;
import org.junit.jupiter.api.condition.DisabledInNativeImage;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;

@DisabledInNativeImage // See GraalVmNativeMongodbSpringStarterSmokeTest for the GraalVM native test
class MongoSpringStarterSmokeTest extends AbstractJvmMongodbSpringStarterSmokeTest {
  @Override
  String getMongoUriProperty() {
    return "spring.data.mongodb.uri";
  }

  @Override
  Class<?> mongoAutoConfigurationClass() {
    return MongoAutoConfiguration.class;
  }

  @Override
  Class<?> mongoInstrumentationAutoConfigurationClass() {
    return MongoClientInstrumentationAutoConfiguration.class;
  }
}
