/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.spring.smoketest;

import com.mongodb.client.MongoClient;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.spring.autoconfigure.OpenTelemetryAutoConfiguration;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.mongo.MongoClientInstrumentationAutoConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;

/** Spring has a test container integration, but that doesn't work for Spring Boot 2 */
public class AbstractJvmMongodbSpringStarterSmokeTest
    extends AbstractMongodbSpringStarterSmokeTest {

  @Container static MongoDBContainer container;

  private ApplicationContextRunner contextRunner;

  @BeforeAll
  static void setUpContainer() {
    container = new MongoDBContainer("mongo:4.0");
    container.start();
  }

  @AfterAll
  static void tearDownContainer() {
    container.stop();
  }

  @BeforeEach
  void setUpContext() {
    contextRunner =
        new ApplicationContextRunner()
            .withAllowBeanDefinitionOverriding(true)
            .withConfiguration(
                AutoConfigurations.of(
                    OpenTelemetryAutoConfiguration.class,
                    SpringSmokeOtelConfiguration.class,
                    MongoAutoConfiguration.class,
                    MongoClientInstrumentationAutoConfiguration.class))
            .withPropertyValues("spring.data.mongodb.uri=" + container.getReplicaSetUrl());
  }

  @Override
  @Test
  void mongodb() {
    contextRunner.run(
        applicationContext -> {
          testing = new SpringSmokeTestRunner(applicationContext.getBean(OpenTelemetry.class));
          mongoClient = applicationContext.getBean(MongoClient.class);
          super.mongodb();
        });
  }
}
