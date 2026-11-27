/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.spring.smoketest;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_TEXT;

import com.mongodb.client.MongoClient;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.spring.autoconfigure.OpenTelemetryAutoConfiguration;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.mongodb.MongoDBContainer;

/** Spring has a test container integration, but that doesn't work for Spring Boot 2 */
public abstract class AbstractJvmMongodbSpringStarterSmokeTest
    extends AbstractMongodbSpringStarterSmokeTest {

  @Container static MongoDBContainer container;

  private ApplicationContextRunner contextRunner;

  abstract String getMongoUriProperty();

  abstract Class<?> mongoAutoConfigurationClass();

  abstract Class<?> mongoInstrumentationAutoConfigurationClass();

  @BeforeAll
  static void setUpContainer() {
    container = new MongoDBContainer("mongo:4.2");
    container.start();
  }

  @AfterAll
  static void tearDownContainer() {
    container.stop();
  }

  @BeforeEach
  void setUpContext() {
    // Closing an application context shuts down its exporters.
    SpringSmokeTestRunner.resetExporters();
    contextRunner =
        new ApplicationContextRunner()
            .withAllowBeanDefinitionOverriding(true)
            .withConfiguration(
                AutoConfigurations.of(
                    OpenTelemetryAutoConfiguration.class,
                    SpringSmokeOtelConfiguration.class,
                    mongoAutoConfigurationClass(),
                    mongoInstrumentationAutoConfigurationClass()))
            .withPropertyValues(getMongoUriProperty() + "=" + container.getReplicaSetUrl());
  }

  @Test
  void capturesQueryTextWhenEnabled() {
    contextRunner =
        contextRunner.withPropertyValues("otel.instrumentation.mongo.capture-query-text=true");
    runMongoClientTest(() -> {
      mongoClient.getDatabase("test").runCommand(new Document("find", "items"));

      testing.waitAndAssertTraces(trace ->
          trace.hasSpansSatisfyingExactly(span ->
              span.hasKind(SpanKind.CLIENT)
                  .hasName("find items")
                  .hasAttribute(satisfies(DB_QUERY_TEXT, val -> val.isNotBlank()))));
    });
  }

  @Override
  protected void runMongoClientTest(Runnable runnable) {
    contextRunner.run(applicationContext -> {
      testing = new SpringSmokeTestRunner(applicationContext.getBean(OpenTelemetry.class));
      mongoClient = applicationContext.getBean(MongoClient.class);

      runnable.run();
    });
  }
}
