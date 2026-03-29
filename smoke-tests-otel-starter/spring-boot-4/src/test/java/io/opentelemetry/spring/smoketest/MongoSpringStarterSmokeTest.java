/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.spring.smoketest;

import io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.mongo.MongoClientInstrumentationSpringBoot4AutoConfiguration;
import org.junit.jupiter.api.condition.DisabledInNativeImage;
import org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration;

@DisabledInNativeImage // See GraalVmNativeMongodbSpringStarterSmokeTest for the GraalVM native test
class MongoSpringStarterSmokeTest extends AbstractJvmMongodbSpringStarterSmokeTest {
  @Override
  String getMongoUriProperty() {
    return "spring.mongodb.uri";
  }

  @Override
  Class<?> mongoAutoConfigurationClass() {
    return MongoAutoConfiguration.class;
  }

  @Override
  Class<?> mongoInstrumentationAutoConfigurationClass() {
    return MongoClientInstrumentationSpringBoot4AutoConfiguration.class;
  }
}
