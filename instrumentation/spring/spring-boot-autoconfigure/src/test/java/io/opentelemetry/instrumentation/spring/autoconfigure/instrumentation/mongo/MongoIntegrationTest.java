/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.instrumentation.mongo;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;

import com.mongodb.client.MongoClient;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import java.time.Duration;
import org.assertj.core.api.AbstractCharSequenceAssert;
import org.assertj.core.api.NumberAssert;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * Reactive Mongo also works, but is not tested here. Reactive has the limitation that the mongo
 * client spans are not children of the server spans, because the reactive client doesn't support
 * context propagation.
 */
class MongoIntegrationTest {

  @RegisterExtension
  static final LibraryInstrumentationExtension testing = LibraryInstrumentationExtension.create();

  static MongoDBContainer mongo;

  private ApplicationContextRunner contextRunner;

  @BeforeAll
  static void setUpMongo() {
    mongo =
        new MongoDBContainer(DockerImageName.parse("mongo:6-jammy"))
            .waitingFor(Wait.forLogMessage(".*Waiting for connections.*", 1))
            .withStartupTimeout(Duration.ofMinutes(1));
    mongo.start();
  }

  @AfterAll
  static void tearDownMongo() {
    mongo.stop();
  }

  @BeforeEach
  void setUpContext() {
    contextRunner =
        new ApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(
                    MongoAutoConfiguration.class,
                    MongoClientInstrumentationAutoConfiguration.class,
                    TestConfig.class))
            .withBean("openTelemetry", OpenTelemetry.class, testing::getOpenTelemetry)
            .withPropertyValues("spring.data.mongodb.uri=" + mongo.getReplicaSetUrl());
  }

  @Test
  void shouldInstrumentClient() {
    contextRunner.run(MongoIntegrationTest::runShouldInstrumentClient);
  }

  private static void runShouldInstrumentClient(ConfigurableApplicationContext applicationContext) {
    MongoTemplate mongoTemplate = applicationContext.getBean(MongoTemplate.class);

    testing.runWithSpan(
        "server",
        () -> {
          mongoTemplate.createCollection("testCollection");
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("server"),
                span ->
                    span.hasName("create test.testCollection")
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            satisfies(
                                ServerAttributes.SERVER_ADDRESS,
                                AbstractCharSequenceAssert::isNotBlank),
                            satisfies(ServerAttributes.SERVER_PORT, NumberAssert::isNotZero),
                            equalTo(DbIncubatingAttributes.DB_SYSTEM, "mongodb"),
                            equalTo(DbIncubatingAttributes.DB_NAME, "test"),
                            equalTo(
                                DbIncubatingAttributes.DB_STATEMENT,
                                "{\"create\": \"testCollection\", \"capped\": \"?\", "
                                    + "\"$db\": \"?\", "
                                    + "\"lsid\": {\"id\": \"?\"}, "
                                    + "\"$readPreference\": {\"mode\": \"?\"}}"),
                            equalTo(DbIncubatingAttributes.DB_OPERATION, "create"),
                            equalTo(
                                DbIncubatingAttributes.DB_MONGODB_COLLECTION, "testCollection"))));
  }

  @Configuration
  static class TestConfig {

    @Bean
    public MongoTemplate mongoTemplate(MongoClient client) {
      return new MongoTemplate(client, "test");
    }
  }
}
