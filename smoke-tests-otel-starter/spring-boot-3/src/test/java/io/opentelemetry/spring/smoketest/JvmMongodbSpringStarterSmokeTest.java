/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import org.junit.jupiter.api.condition.DisabledInNativeImage;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@DisabledInNativeImage // See GraalVmNativeMongodbSpringStarterSmokeTest for the GraalVM native test
public class JvmMongodbSpringStarterSmokeTest extends AbstractMongodbSpringStarterSmokeTest {

  @Container @ServiceConnection
  static MongoDBContainer container = new MongoDBContainer("mongo:latest");
}
