/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package spring.jpa;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.api.trace.SpanKind.INTERNAL;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SQL_TABLE;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.hibernate.Version;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class SpringJpaTest {
  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static AnnotationConfigApplicationContext context;
  private static CustomerRepository repo;

  @BeforeAll
  static void setUp() {
    context = new AnnotationConfigApplicationContext(PersistenceConfig.class);
    repo = context.getBean(CustomerRepository.class);
  }

  @AfterAll
  static void tearDown() {
    if (context != null) {
      context.close();
    }
  }

  @SuppressWarnings("deprecation") // DbIncubatingAttributes.DB_NAME has been deprecated
  @Test
  void testCurd() {
    boolean isHibernate4 = Version.getVersionString().startsWith("4.");
    Customer customer = new Customer("Bob", "Anonymous");

    assertNull(customer.getId());
    assertFalse(testing.runWithSpan("parent", () -> repo.findAll().iterator().hasNext()));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("parent")
                        .hasKind(INTERNAL)
                        .hasNoParent()
                        .hasAttributes(Attributes.empty()),
                span ->
                    span.hasName("SELECT spring.jpa.Customer")
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            satisfies(
                                AttributeKey.stringKey("hibernate.session_id"),
                                val -> val.isInstanceOf(String.class))),
                span ->
                    span.hasName("SELECT test.Customer")
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                DB_SYSTEM, DbIncubatingAttributes.DbSystemIncubatingValues.HSQLDB),
                            equalTo(maybeStable(DB_NAME), "test"),
                            equalTo(
                                DbIncubatingAttributes.DB_USER,
                                emitStableDatabaseSemconv() ? null : "sa"),
                            equalTo(
                                DbIncubatingAttributes.DB_CONNECTION_STRING,
                                emitStableDatabaseSemconv() ? null : "hsqldb:mem:"),
                            satisfies(
                                maybeStable(DB_STATEMENT),
                                val ->
                                    val.matches(
                                        "select ([^.]+)\\.id([^,]*),([^.]+)\\.firstName([^,]*),([^.]+)\\.lastName(.*)from Customer(.*)")),
                            equalTo(maybeStable(DB_OPERATION), "SELECT"),
                            equalTo(maybeStable(DB_SQL_TABLE), "Customer")),
                span ->
                    span.hasName("Transaction.commit")
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                AttributeKey.stringKey("hibernate.session_id"),
                                trace
                                    .getSpan(1)
                                    .getAttributes()
                                    .get(stringKey("hibernate.session_id"))))));

    testing.clearData();

    testing.runWithSpan("parent", () -> repo.save(customer));
    Long savedId = customer.getId();

    assertThat(customer.getId()).isNotNull();
    testing.waitAndAssertTraces(
        trace -> {
          List<Consumer<SpanDataAssert>> consumers =
              new ArrayList<>(
                  asList(
                      span ->
                          span.hasName("parent")
                              .hasKind(INTERNAL)
                              .hasNoParent()
                              .hasAttributes(Attributes.empty()),
                      span ->
                          span.hasName("Session.persist spring.jpa.Customer")
                              .hasKind(INTERNAL)
                              .hasParent(trace.getSpan(0))
                              .hasAttributesSatisfyingExactly(
                                  satisfies(
                                      AttributeKey.stringKey("hibernate.session_id"),
                                      val -> val.isInstanceOf(String.class)))));
          if (!isHibernate4) {
            consumers.addAll(
                asList(
                    span ->
                        span.hasName("CALL test")
                            .hasKind(CLIENT)
                            .hasParent(trace.getSpan(1))
                            .hasAttributesSatisfyingExactly(
                                equalTo(
                                    DB_SYSTEM,
                                    DbIncubatingAttributes.DbSystemIncubatingValues.HSQLDB),
                                equalTo(maybeStable(DB_NAME), "test"),
                                equalTo(
                                    DbIncubatingAttributes.DB_USER,
                                    emitStableDatabaseSemconv() ? null : "sa"),
                                equalTo(
                                    maybeStable(DB_STATEMENT), "call next value for Customer_SEQ"),
                                equalTo(
                                    DbIncubatingAttributes.DB_CONNECTION_STRING,
                                    emitStableDatabaseSemconv() ? null : "hsqldb:mem:"),
                                equalTo(maybeStable(DB_OPERATION), "CALL")),
                    span ->
                        span.hasName("Transaction.commit")
                            .hasKind(INTERNAL)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                equalTo(
                                    AttributeKey.stringKey("hibernate.session_id"),
                                    trace
                                        .getSpan(1)
                                        .getAttributes()
                                        .get(stringKey("hibernate.session_id")))),
                    span ->
                        span.hasName("INSERT test.Customer")
                            .hasKind(CLIENT)
                            .hasParent(trace.getSpan(3))
                            .hasAttributesSatisfyingExactly(
                                equalTo(
                                    DB_SYSTEM,
                                    DbIncubatingAttributes.DbSystemIncubatingValues.HSQLDB),
                                equalTo(maybeStable(DB_NAME), "test"),
                                equalTo(
                                    DbIncubatingAttributes.DB_USER,
                                    emitStableDatabaseSemconv() ? null : "sa"),
                                equalTo(
                                    DbIncubatingAttributes.DB_CONNECTION_STRING,
                                    emitStableDatabaseSemconv() ? null : "hsqldb:mem:"),
                                satisfies(
                                    maybeStable(DB_STATEMENT),
                                    val ->
                                        val.matches(
                                            "insert into Customer \\(.*\\) values \\(.*\\)")),
                                equalTo(maybeStable(DB_OPERATION), "INSERT"),
                                equalTo(maybeStable(DB_SQL_TABLE), "Customer"))));

          } else {
            consumers.addAll(
                asList(
                    span ->
                        span.hasName("INSERT test.Customer")
                            .hasKind(CLIENT)
                            .hasParent(trace.getSpan(1))
                            .hasAttributesSatisfyingExactly(
                                equalTo(
                                    DB_SYSTEM,
                                    DbIncubatingAttributes.DbSystemIncubatingValues.HSQLDB),
                                equalTo(maybeStable(DB_NAME), "test"),
                                equalTo(
                                    DbIncubatingAttributes.DB_USER,
                                    emitStableDatabaseSemconv() ? null : "sa"),
                                equalTo(
                                    DbIncubatingAttributes.DB_CONNECTION_STRING,
                                    emitStableDatabaseSemconv() ? null : "hsqldb:mem:"),
                                satisfies(
                                    maybeStable(DB_STATEMENT),
                                    val ->
                                        val.matches(
                                            "insert into Customer \\(.*\\) values \\(.*\\)")),
                                equalTo(maybeStable(DB_OPERATION), "INSERT"),
                                equalTo(maybeStable(DB_SQL_TABLE), "Customer")),
                    span ->
                        span.hasName("Transaction.commit")
                            .hasKind(INTERNAL)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                equalTo(
                                    AttributeKey.stringKey("hibernate.session_id"),
                                    trace
                                        .getSpan(1)
                                        .getAttributes()
                                        .get(stringKey("hibernate.session_id"))))));
          }
          trace.hasSpansSatisfyingExactly(consumers);
        });

    testing.clearData();

    customer.setFirstName("Bill");
    testing.runWithSpan("parent", () -> repo.save(customer));

    assertThat(customer.getId()).isEqualTo(savedId);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("parent")
                        .hasKind(INTERNAL)
                        .hasNoParent()
                        .hasAttributes(Attributes.empty()),
                span ->
                    span.hasName("Session.merge spring.jpa.Customer")
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            satisfies(
                                AttributeKey.stringKey("hibernate.session_id"),
                                val -> val.isInstanceOf(String.class))),
                span ->
                    span.hasName("SELECT test.Customer")
                        .hasKind(CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                DB_SYSTEM, DbIncubatingAttributes.DbSystemIncubatingValues.HSQLDB),
                            equalTo(maybeStable(DB_NAME), "test"),
                            equalTo(
                                DbIncubatingAttributes.DB_USER,
                                emitStableDatabaseSemconv() ? null : "sa"),
                            equalTo(
                                DbIncubatingAttributes.DB_CONNECTION_STRING,
                                emitStableDatabaseSemconv() ? null : "hsqldb:mem:"),
                            satisfies(
                                maybeStable(DB_STATEMENT),
                                val ->
                                    val.matches(
                                        "select ([^.]+)\\.id([^,]*),([^.]+)\\.firstName([^,]*),([^.]+)\\.lastName (.*)from Customer (.*)where ([^.]+)\\.id( ?)=( ?)\\?")),
                            equalTo(maybeStable(DB_OPERATION), "SELECT"),
                            equalTo(maybeStable(DB_SQL_TABLE), "Customer")),
                span ->
                    span.hasName("Transaction.commit")
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                AttributeKey.stringKey("hibernate.session_id"),
                                trace
                                    .getSpan(1)
                                    .getAttributes()
                                    .get(stringKey("hibernate.session_id")))),
                span ->
                    span.hasName("UPDATE test.Customer")
                        .hasKind(CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                DB_SYSTEM, DbIncubatingAttributes.DbSystemIncubatingValues.HSQLDB),
                            equalTo(maybeStable(DB_NAME), "test"),
                            equalTo(
                                DbIncubatingAttributes.DB_USER,
                                emitStableDatabaseSemconv() ? null : "sa"),
                            equalTo(
                                DbIncubatingAttributes.DB_CONNECTION_STRING,
                                emitStableDatabaseSemconv() ? null : "hsqldb:mem:"),
                            satisfies(
                                maybeStable(DB_STATEMENT),
                                val ->
                                    val.matches(
                                        "update Customer set firstName=\\?,(.*)lastName=\\? where id=\\?")),
                            equalTo(maybeStable(DB_OPERATION), "UPDATE"),
                            equalTo(maybeStable(DB_SQL_TABLE), "Customer"))));
    testing.clearData();
    Customer anonymousCustomer =
        testing.runWithSpan("parent", () -> repo.findByLastName("Anonymous").get(0));

    assertThat(anonymousCustomer.getId()).isEqualTo(savedId);
    assertThat(anonymousCustomer.getFirstName()).isEqualTo("Bill");
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("parent")
                        .hasKind(INTERNAL)
                        .hasNoParent()
                        .hasAttributes(Attributes.empty()),
                span ->
                    span.satisfies(
                            val ->
                                assertThat(val.getName())
                                    .isIn(asList("SELECT spring.jpa.Customer", "Hibernate Query")))
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            satisfies(
                                AttributeKey.stringKey("hibernate.session_id"),
                                val -> val.isInstanceOf(String.class))),
                span ->
                    span.hasName("SELECT test.Customer")
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                DB_SYSTEM, DbIncubatingAttributes.DbSystemIncubatingValues.HSQLDB),
                            equalTo(maybeStable(DB_NAME), "test"),
                            equalTo(
                                DbIncubatingAttributes.DB_USER,
                                emitStableDatabaseSemconv() ? null : "sa"),
                            equalTo(
                                DbIncubatingAttributes.DB_CONNECTION_STRING,
                                emitStableDatabaseSemconv() ? null : "hsqldb:mem:"),
                            satisfies(
                                maybeStable(DB_STATEMENT),
                                val ->
                                    val.matches(
                                        "select ([^.]+)\\.id([^,]*),([^.]+)\\.firstName([^,]*),([^.]+)\\.lastName (.*)from Customer (.*)(where ([^.]+)\\.lastName( ?)=( ?)\\?|)")),
                            equalTo(maybeStable(DB_OPERATION), "SELECT"),
                            equalTo(maybeStable(DB_SQL_TABLE), "Customer"))));
    testing.clearData();

    testing.runWithSpan("parent", () -> repo.delete(anonymousCustomer));

    testing.waitAndAssertTraces(
        trace -> {
          List<Consumer<SpanDataAssert>> consumers =
              new ArrayList<>(
                  asList(
                      span ->
                          span.hasName("parent")
                              .hasKind(INTERNAL)
                              .hasNoParent()
                              .hasAttributes(Attributes.empty())));
          if (!isHibernate4) {
            consumers.add(
                span ->
                    span.satisfies(
                            val ->
                                assertThat(val.getName())
                                    .matches("Session.(get|find) spring.jpa.Customer"))
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            satisfies(
                                AttributeKey.stringKey("hibernate.session_id"),
                                val -> val.isInstanceOf(String.class))));

            consumers.add(
                span ->
                    span.hasName("SELECT test.Customer")
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                DB_SYSTEM, DbIncubatingAttributes.DbSystemIncubatingValues.HSQLDB),
                            equalTo(maybeStable(DB_NAME), "test"),
                            equalTo(
                                DbIncubatingAttributes.DB_USER,
                                emitStableDatabaseSemconv() ? null : "sa"),
                            equalTo(
                                DbIncubatingAttributes.DB_CONNECTION_STRING,
                                emitStableDatabaseSemconv() ? null : "hsqldb:mem:"),
                            satisfies(
                                maybeStable(DB_STATEMENT),
                                val ->
                                    val.matches(
                                        "select ([^.]+)\\.id([^,]*),([^.]+)\\.firstName([^,]*),([^.]+)\\.lastName (.*)from Customer (.*)(where ([^.]+)\\.lastName( ?)=( ?)\\?|)")),
                            equalTo(maybeStable(DB_OPERATION), "SELECT"),
                            equalTo(maybeStable(DB_SQL_TABLE), "Customer")));
          }
          consumers.add(
              span ->
                  span.hasName("Session.merge spring.jpa.Customer")
                      .hasKind(INTERNAL)
                      .hasParent(trace.getSpan(0))
                      .hasAttributesSatisfyingExactly(
                          satisfies(
                              AttributeKey.stringKey("hibernate.session_id"),
                              val -> val.isInstanceOf(String.class))));
          if (isHibernate4) {
            consumers.add(
                span ->
                    span.hasName("SELECT test.Customer")
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                DB_SYSTEM, DbIncubatingAttributes.DbSystemIncubatingValues.HSQLDB),
                            equalTo(maybeStable(DB_NAME), "test"),
                            equalTo(
                                DbIncubatingAttributes.DB_USER,
                                emitStableDatabaseSemconv() ? null : "sa"),
                            equalTo(
                                DbIncubatingAttributes.DB_CONNECTION_STRING,
                                emitStableDatabaseSemconv() ? null : "hsqldb:mem:"),
                            satisfies(
                                maybeStable(DB_STATEMENT),
                                val ->
                                    val.matches(
                                        "select ([^.]+)\\.id([^,]*),([^.]+)\\.firstName([^,]*),([^.]+)\\.lastName (.*)from Customer (.*)where ([^.]+)\\.id( ?)=( ?)\\?")),
                            equalTo(maybeStable(DB_OPERATION), "SELECT"),
                            equalTo(maybeStable(DB_SQL_TABLE), "Customer")));
          }
          consumers.add(
              span ->
                  span.hasName("Session.delete spring.jpa.Customer")
                      .hasKind(INTERNAL)
                      .hasParent(trace.getSpan(0))
                      .hasAttributesSatisfyingExactly(
                          satisfies(
                              AttributeKey.stringKey("hibernate.session_id"),
                              val -> val.isInstanceOf(String.class))));
          consumers.add(
              span ->
                  span.hasName("Transaction.commit")
                      .hasKind(INTERNAL)
                      .hasParent(trace.getSpan(0))
                      .hasAttributesSatisfyingExactly(
                          satisfies(
                              AttributeKey.stringKey("hibernate.session_id"),
                              val -> val.isInstanceOf(String.class))));
          consumers.add(
              span ->
                  span.hasName("DELETE test.Customer")
                      .hasKind(CLIENT)
                      .hasAttributesSatisfyingExactly(
                          equalTo(
                              DB_SYSTEM, DbIncubatingAttributes.DbSystemIncubatingValues.HSQLDB),
                          equalTo(maybeStable(DB_NAME), "test"),
                          equalTo(
                              DbIncubatingAttributes.DB_USER,
                              emitStableDatabaseSemconv() ? null : "sa"),
                          equalTo(
                              DbIncubatingAttributes.DB_CONNECTION_STRING,
                              emitStableDatabaseSemconv() ? null : "hsqldb:mem:"),
                          equalTo(maybeStable(DB_STATEMENT), "delete from Customer where id=?"),
                          equalTo(maybeStable(DB_OPERATION), "DELETE"),
                          equalTo(maybeStable(DB_SQL_TABLE), "Customer")));
          trace.hasSpansSatisfyingExactly(consumers);
        });
  }
}
