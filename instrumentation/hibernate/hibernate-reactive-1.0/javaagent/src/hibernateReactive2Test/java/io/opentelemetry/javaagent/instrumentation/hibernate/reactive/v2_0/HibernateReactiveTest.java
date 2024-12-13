/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate.reactive.v2_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SQL_TABLE;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_USER;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.vertx.core.Vertx;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.hibernate.reactive.mutiny.Mutiny;
import org.hibernate.reactive.stage.Stage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;

class HibernateReactiveTest {
  private static final Logger logger = LoggerFactory.getLogger(HibernateReactiveTest.class);

  private static final String USER_DB = "SA";
  private static final String PW_DB = "password123";
  private static final String DB = "tempdb";

  @RegisterExtension
  protected static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final Vertx vertx = Vertx.vertx();
  private static GenericContainer<?> container;
  private static String host;
  private static int port;
  private static EntityManagerFactory entityManagerFactory;
  private static Mutiny.SessionFactory mutinySessionFactory;
  private static Stage.SessionFactory stageSessionFactory;

  @BeforeAll
  static void setUp() throws Exception {
    container =
        new GenericContainer<>("postgres:9.6.8")
            .withEnv("POSTGRES_USER", USER_DB)
            .withEnv("POSTGRES_PASSWORD", PW_DB)
            .withEnv("POSTGRES_DB", DB)
            .withExposedPorts(5432)
            .withLogConsumer(new Slf4jLogConsumer(logger))
            .withStartupTimeout(Duration.ofMinutes(2));
    container.start();

    host = container.getHost();
    port = container.getMappedPort(5432);
    System.setProperty("db.host", host);
    System.setProperty("db.port", String.valueOf(port));

    entityManagerFactory =
        vertx
            .getOrCreateContext()
            .<EntityManagerFactory>executeBlocking(
                promise -> promise.complete(Persistence.createEntityManagerFactory("test-pu")))
            .toCompletionStage()
            .toCompletableFuture()
            .get(30, TimeUnit.SECONDS);

    Value value = new Value("name");
    value.setId(1L);

    mutinySessionFactory = entityManagerFactory.unwrap(Mutiny.SessionFactory.class);
    stageSessionFactory = entityManagerFactory.unwrap(Stage.SessionFactory.class);

    mutinySessionFactory
        .withTransaction((session, tx) -> session.merge(value))
        .await()
        .atMost(Duration.ofSeconds(30));
  }

  @AfterAll
  static void cleanUp() {
    if (entityManagerFactory != null) {
      entityManagerFactory.close();
    }
    if (mutinySessionFactory != null) {
      mutinySessionFactory.close();
    }
    if (stageSessionFactory != null) {
      stageSessionFactory.close();
    }
    vertx.close();
    container.stop();
  }

  @Test
  void testMutiny() {
    testing.runWithSpan(
        "parent",
        () -> {
          mutinySessionFactory
              .withSession(
                  session -> {
                    if (!Span.current().getSpanContext().isValid()) {
                      throw new IllegalStateException("missing parent span");
                    }

                    return session
                        .find(Value.class, 1L)
                        .invoke(value -> testing.runWithSpan("callback", () -> {}));
                  })
              .await()
              .atMost(Duration.ofSeconds(30));
        });

    assertTrace();
  }

  @Test
  void testStage() throws Exception {
    testing
        .runWithSpan(
            "parent",
            () ->
                stageSessionFactory
                    .withSession(
                        session -> {
                          if (!Span.current().getSpanContext().isValid()) {
                            throw new IllegalStateException("missing parent span");
                          }

                          return session
                              .find(Value.class, 1L)
                              .thenAccept(value -> testing.runWithSpan("callback", () -> {}));
                        })
                    .toCompletableFuture())
        .get(30, TimeUnit.SECONDS);

    assertTrace();
  }

  @Test
  void testStageWithStatelessSession() throws Exception {
    testing
        .runWithSpan(
            "parent",
            () ->
                stageSessionFactory
                    .withStatelessSession(
                        session -> {
                          if (!Span.current().getSpanContext().isValid()) {
                            throw new IllegalStateException("missing parent span");
                          }

                          return session
                              .get(Value.class, 1L)
                              .thenAccept(value -> testing.runWithSpan("callback", () -> {}));
                        })
                    .toCompletableFuture())
        .get(30, TimeUnit.SECONDS);

    assertTrace();
  }

  @Test
  void testStageSessionWithTransaction() throws Exception {
    testing
        .runWithSpan(
            "parent",
            () ->
                stageSessionFactory
                    .withSession(
                        session -> {
                          if (!Span.current().getSpanContext().isValid()) {
                            throw new IllegalStateException("missing parent span");
                          }

                          return session
                              .withTransaction(transaction -> session.find(Value.class, 1L))
                              .thenAccept(value -> testing.runWithSpan("callback", () -> {}));
                        })
                    .toCompletableFuture())
        .get(30, TimeUnit.SECONDS);

    assertTrace();
  }

  @Test
  void testStageStatelessSessionWithTransaction() throws Exception {
    testing
        .runWithSpan(
            "parent",
            () ->
                stageSessionFactory
                    .withStatelessSession(
                        session -> {
                          if (!Span.current().getSpanContext().isValid()) {
                            throw new IllegalStateException("missing parent span");
                          }

                          return session
                              .withTransaction(transaction -> session.get(Value.class, 1L))
                              .thenAccept(value -> testing.runWithSpan("callback", () -> {}));
                        })
                    .toCompletableFuture())
        .get(30, TimeUnit.SECONDS);

    assertTrace();
  }

  @Test
  void testStageOpenSession() throws Exception {
    CompletableFuture<Object> result = new CompletableFuture<>();
    testing.runWithSpan(
        "parent",
        () ->
            runWithVertx(
                () ->
                    stageSessionFactory
                        .openSession()
                        .thenApply(
                            session -> {
                              if (!Span.current().getSpanContext().isValid()) {
                                throw new IllegalStateException("missing parent span");
                              }

                              return session
                                  .find(Value.class, 1L)
                                  .thenAccept(value -> testing.runWithSpan("callback", () -> {}));
                            })
                        .whenComplete((value, throwable) -> complete(result, value, throwable))));
    result.get(30, TimeUnit.SECONDS);

    assertTrace();
  }

  @Test
  void testStageOpenStatelessSession() throws Exception {
    CompletableFuture<Object> result = new CompletableFuture<>();
    testing.runWithSpan(
        "parent",
        () ->
            runWithVertx(
                () ->
                    stageSessionFactory
                        .openStatelessSession()
                        .thenApply(
                            session -> {
                              if (!Span.current().getSpanContext().isValid()) {
                                throw new IllegalStateException("missing parent span");
                              }

                              return session
                                  .get(Value.class, 1L)
                                  .thenAccept(value -> testing.runWithSpan("callback", () -> {}));
                            })
                        .whenComplete((value, throwable) -> complete(result, value, throwable))));
    result.get(30, TimeUnit.SECONDS);

    assertTrace();
  }

  private static void runWithVertx(Runnable runnable) {
    vertx.getOrCreateContext().runOnContext(event -> runnable.run());
  }

  private static void complete(
      CompletableFuture<Object> completableFuture, Object result, Throwable throwable) {
    if (throwable != null) {
      completableFuture.completeExceptionally(throwable);
    } else {
      completableFuture.complete(result);
    }
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  private static void assertTrace() {
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL),
                span ->
                    span.hasName("SELECT tempdb.Value")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_NAME), DB),
                            equalTo(DB_USER, emitStableDatabaseSemconv() ? null : USER_DB),
                            equalTo(
                                maybeStable(DB_STATEMENT),
                                "select v1_0.id,v1_0.name from Value v1_0 where v1_0.id=$1"),
                            equalTo(maybeStable(DB_OPERATION), "SELECT"),
                            equalTo(maybeStable(DB_SQL_TABLE), "Value"),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port)),
                span ->
                    span.hasName("callback")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))));
  }
}
