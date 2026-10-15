/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate.reactive.v2_0;

import static io.opentelemetry.instrumentation.testing.junit.service.SemconvServiceStabilityUtil.maybeStablePeerService;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_SUMMARY;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_TEXT;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.POSTGRESQL;
import static java.util.concurrent.TimeUnit.SECONDS;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.vertx.core.Vertx;
import jakarta.persistence.EntityManagerFactory;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.hibernate.reactive.mutiny.Mutiny;
import org.hibernate.reactive.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;

@SuppressWarnings("InterruptedExceptionSwallowed")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractHibernateReactiveTest {
  private static final Logger logger = LoggerFactory.getLogger(AbstractHibernateReactiveTest.class);

  private static final String USER_DB = "SA";
  private static final String PW_DB = "password123";
  private static final String DB = "tempdb";

  @RegisterExtension
  protected static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  protected final Vertx vertx = Vertx.vertx();
  private String host;
  private int port;
  private Mutiny.SessionFactory mutinySessionFactory;
  private Stage.SessionFactory stageSessionFactory;

  protected abstract EntityManagerFactory createEntityManagerFactory() throws Exception;

  @BeforeAll
  void setUp() throws Exception {
    cleanup.deferAfterAll(vertx::close);
    GenericContainer<?> container =
        new GenericContainer<>("postgres:9.6.8")
            .withEnv("POSTGRES_USER", USER_DB)
            .withEnv("POSTGRES_PASSWORD", PW_DB)
            .withEnv("POSTGRES_DB", DB)
            .withExposedPorts(5432)
            .withLogConsumer(new Slf4jLogConsumer(logger))
            .withStartupTimeout(Duration.ofMinutes(2));
    cleanup.deferAfterAll(container::stop);
    container.start();

    host = container.getHost();
    port = container.getMappedPort(5432);
    System.setProperty("db.host", host);
    System.setProperty("db.port", String.valueOf(port));

    EntityManagerFactory entityManagerFactory = createEntityManagerFactory();
    cleanup.deferAfterAll(entityManagerFactory);

    Value value = new Value("name");
    value.setId(1L);

    mutinySessionFactory = entityManagerFactory.unwrap(Mutiny.SessionFactory.class);
    stageSessionFactory = entityManagerFactory.unwrap(Stage.SessionFactory.class);
    cleanup.deferAfterAll(mutinySessionFactory);
    cleanup.deferAfterAll(stageSessionFactory);

    mutinySessionFactory
        .withTransaction((session, tx) -> session.merge(value))
        .await()
        .atMost(Duration.ofSeconds(30));
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
        .get(30, SECONDS);

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
        .get(30, SECONDS);

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
        .get(30, SECONDS);

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
        .get(30, SECONDS);

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
                        .thenCompose(
                            session -> {
                              if (!Span.current().getSpanContext().isValid()) {
                                throw new IllegalStateException("missing parent span");
                              }

                              return session
                                  .find(Value.class, 1L)
                                  .thenAccept(value -> testing.runWithSpan("callback", () -> {}));
                            })
                        .whenComplete((value, throwable) -> complete(result, null, throwable))));
    result.get(30, SECONDS);

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
                        .thenCompose(
                            session -> {
                              if (!Span.current().getSpanContext().isValid()) {
                                throw new IllegalStateException("missing parent span");
                              }

                              return session
                                  .get(Value.class, 1L)
                                  .thenAccept(value -> testing.runWithSpan("callback", () -> {}));
                            })
                        .whenComplete((value, throwable) -> complete(result, null, throwable))));
    result.get(30, SECONDS);

    assertTrace();
  }

  private void runWithVertx(Runnable runnable) {
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
  private void assertTrace() {
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL),
                span ->
                    span.hasName("select Value")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, POSTGRESQL),
                            equalTo(DB_NAMESPACE, DB),
                            equalTo(
                                DB_QUERY_TEXT,
                                "select v1_0.id,v1_0.name from Value v1_0 where v1_0.id=$1"),
                            equalTo(DB_QUERY_SUMMARY, "select Value"),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(maybeStablePeerService(), "test-peer-service")),
                span ->
                    span.hasName("callback")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))));
  }
}
