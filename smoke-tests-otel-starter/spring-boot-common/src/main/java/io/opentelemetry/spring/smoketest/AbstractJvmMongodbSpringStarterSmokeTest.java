/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.spring.smoketest;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_TEXT;

import com.mongodb.client.MongoClient;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.spring.autoconfigure.OpenTelemetryAutoConfiguration;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
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
  void omitsQueryTextByDefault() {
    assertMongoQueryText(contextRunner, equalTo(DB_QUERY_TEXT, null));
  }

  @Test
  void capturesSanitizedQueryTextWhenEnabled() {
    assertMongoQueryText(
        contextRunner.withPropertyValues("otel.instrumentation.mongo.capture-query-text=true"),
        satisfies(
            DB_QUERY_TEXT,
            val ->
                val.isNotNull()
                    .satisfies(v ->
                        assertThat(Document.parse(v).get("filter", Document.class))
                            .isEqualTo(new Document("item", "?")))));
  }

  private void assertMongoQueryText(
      ApplicationContextRunner runner, AttributeAssertion queryTextAssertion) {
    runMongoClientTest(
        runner,
        () -> {
          mongoClient
              .getDatabase("test")
              .runCommand(
                  new Document("find", "items")
                      .append("filter", new Document("item", "example-value")));

          testing.waitAndAssertTraces(trace ->
              trace.hasSpansSatisfyingExactly(span ->
                  span.hasKind(SpanKind.CLIENT)
                      .hasName("find items")
                      .hasAttributesSatisfying(queryTextAssertion)));
        });
  }

  @Override
  protected void runMongoClientTest(Runnable runnable) {
    runMongoClientTest(contextRunner, runnable);
  }

  private void runMongoClientTest(ApplicationContextRunner runner, Runnable runnable) {
    runner.run(applicationContext -> {
      testing = new SpringSmokeTestRunner(applicationContext.getBean(OpenTelemetry.class));
      mongoClient = applicationContext.getBean(MongoClient.class);

      runnable.run();
    });
  }
}
