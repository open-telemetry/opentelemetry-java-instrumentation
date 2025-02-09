/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.data;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_CONNECTION_STRING;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SQL_TABLE;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_USER;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.semconv.incubating.CodeIncubatingAttributes;
import java.util.List;
import java.util.Optional;
import org.hibernate.Version;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.data.jpa.repository.JpaRepository;

public abstract class AbstractSpringJpaTest<
    ENTITY, REPOSITORY extends JpaRepository<ENTITY, Long>> {

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  protected abstract ENTITY newCustomer(String firstName, String lastName);

  protected abstract Long id(ENTITY customer);

  protected abstract void setFirstName(ENTITY customer, String firstName);

  protected abstract Class<REPOSITORY> repositoryClass();

  protected abstract REPOSITORY repository();

  protected abstract List<ENTITY> findByLastName(REPOSITORY repository, String lastName);

  protected abstract List<ENTITY> findSpecialCustomers(REPOSITORY repository);

  protected abstract Optional<ENTITY> findOneByLastName(REPOSITORY repository, String lastName);

  protected void clearData() {
    testing.clearData();
  }

  @Test
  void testObjectMethod() {
    REPOSITORY repo = repository();

    testing.runWithSpan("toString test", repo::toString);

    // Asserting that a span is NOT created for toString
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("toString test").hasTotalAttributeCount(0)));
  }

  @SuppressWarnings("deprecation") // TODO DB_CONNECTION_STRING deprecation
  static void assertHibernate4Trace(TraceAssert trace, String repoClassName) {
    trace.hasSpansSatisfyingExactly(
        span ->
            span.hasName("JpaCustomerRepository.save")
                .hasKind(SpanKind.INTERNAL)
                .hasAttributesSatisfyingExactly(
                    equalTo(CodeIncubatingAttributes.CODE_NAMESPACE, repoClassName),
                    equalTo(CodeIncubatingAttributes.CODE_FUNCTION, "save")),
        span ->
            span.hasName("INSERT test.JpaCustomer")
                .hasKind(SpanKind.CLIENT)
                .hasParent(trace.getSpan(0))
                .hasAttributesSatisfyingExactly(
                    equalTo(maybeStable(DB_SYSTEM), "hsqldb"),
                    equalTo(maybeStable(DB_NAME), "test"),
                    equalTo(DB_USER, emitStableDatabaseSemconv() ? null : "sa"),
                    equalTo(
                        DB_CONNECTION_STRING, emitStableDatabaseSemconv() ? null : "hsqldb:mem:"),
                    satisfies(maybeStable(DB_STATEMENT), val -> val.startsWith("insert ")),
                    equalTo(maybeStable(DB_OPERATION), "INSERT"),
                    equalTo(maybeStable(DB_SQL_TABLE), "JpaCustomer")));
  }

  @SuppressWarnings("deprecation") // TODO DB_CONNECTION_STRING deprecation
  static void assertHibernateTrace(TraceAssert trace, String repoClassName) {
    trace.hasSpansSatisfyingExactly(
        span ->
            span.hasName("JpaCustomerRepository.save")
                .hasKind(SpanKind.INTERNAL)
                .hasAttributesSatisfyingExactly(
                    equalTo(CodeIncubatingAttributes.CODE_NAMESPACE, repoClassName),
                    equalTo(CodeIncubatingAttributes.CODE_FUNCTION, "save")),
        span ->
            span.hasName("CALL test")
                .hasKind(SpanKind.CLIENT)
                .hasAttributesSatisfyingExactly(
                    equalTo(maybeStable(DB_SYSTEM), "hsqldb"),
                    equalTo(maybeStable(DB_NAME), "test"),
                    equalTo(DB_USER, emitStableDatabaseSemconv() ? null : "sa"),
                    equalTo(
                        DB_CONNECTION_STRING, emitStableDatabaseSemconv() ? null : "hsqldb:mem:"),
                    satisfies(
                        maybeStable(DB_STATEMENT), val -> val.startsWith("call next value for ")),
                    equalTo(maybeStable(DB_OPERATION), "CALL")),
        span ->
            span.hasName("INSERT test.JpaCustomer")
                .hasKind(SpanKind.CLIENT)
                .hasParent(trace.getSpan(0))
                .hasAttributesSatisfyingExactly(
                    equalTo(maybeStable(DB_SYSTEM), "hsqldb"),
                    equalTo(maybeStable(DB_NAME), "test"),
                    equalTo(DB_USER, emitStableDatabaseSemconv() ? null : "sa"),
                    equalTo(
                        DB_CONNECTION_STRING, emitStableDatabaseSemconv() ? null : "hsqldb:mem:"),
                    satisfies(maybeStable(DB_STATEMENT), val -> val.startsWith("insert ")),
                    equalTo(maybeStable(DB_OPERATION), "INSERT"),
                    equalTo(maybeStable(DB_SQL_TABLE), "JpaCustomer")));
  }

  @SuppressWarnings("deprecation") // TODO DB_CONNECTION_STRING deprecation
  @Test
  void testCrud() {
    boolean isHibernate4 = Version.getVersionString().startsWith("4.");
    REPOSITORY repo = repository();
    String repoClassName = repositoryClass().getName();

    ENTITY customer = newCustomer("Bob", "Anonymous");

    assertNull(id(customer));
    assertFalse(repo.findAll().iterator().hasNext());

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("JpaCustomerRepository.findAll")
                        .hasKind(SpanKind.INTERNAL)
                        .hasAttributesSatisfyingExactly(
                            equalTo(CodeIncubatingAttributes.CODE_NAMESPACE, repoClassName),
                            equalTo(CodeIncubatingAttributes.CODE_FUNCTION, "findAll")),
                span ->
                    span.hasName("SELECT test.JpaCustomer")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), "hsqldb"),
                            equalTo(maybeStable(DB_NAME), "test"),
                            equalTo(DB_USER, emitStableDatabaseSemconv() ? null : "sa"),
                            equalTo(
                                DB_CONNECTION_STRING,
                                emitStableDatabaseSemconv() ? null : "hsqldb:mem:"),
                            satisfies(maybeStable(DB_STATEMENT), val -> val.startsWith("select ")),
                            equalTo(maybeStable(DB_OPERATION), "SELECT"),
                            equalTo(maybeStable(DB_SQL_TABLE), "JpaCustomer"))));
    clearData();

    repo.save(customer);
    assertNotNull(id(customer));
    Long savedId = id(customer);
    if (isHibernate4) {
      testing.waitAndAssertTraces(trace -> assertHibernate4Trace(trace, repoClassName));
    } else {
      testing.waitAndAssertTraces(trace -> assertHibernateTrace(trace, repoClassName));
    }
    clearData();

    setFirstName(customer, "Bill");
    repo.save(customer);
    assertEquals(id(customer), savedId);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("JpaCustomerRepository.save")
                        .hasKind(SpanKind.INTERNAL)
                        .hasAttributesSatisfyingExactly(
                            equalTo(CodeIncubatingAttributes.CODE_NAMESPACE, repoClassName),
                            equalTo(CodeIncubatingAttributes.CODE_FUNCTION, "save")),
                span ->
                    span.hasName("SELECT test.JpaCustomer")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), "hsqldb"),
                            equalTo(maybeStable(DB_NAME), "test"),
                            equalTo(DB_USER, emitStableDatabaseSemconv() ? null : "sa"),
                            equalTo(
                                DB_CONNECTION_STRING,
                                emitStableDatabaseSemconv() ? null : "hsqldb:mem:"),
                            satisfies(maybeStable(DB_STATEMENT), val -> val.startsWith("select ")),
                            equalTo(maybeStable(DB_OPERATION), "SELECT"),
                            equalTo(maybeStable(DB_SQL_TABLE), "JpaCustomer")),
                span ->
                    span.hasName("UPDATE test.JpaCustomer")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), "hsqldb"),
                            equalTo(maybeStable(DB_NAME), "test"),
                            equalTo(DB_USER, emitStableDatabaseSemconv() ? null : "sa"),
                            equalTo(
                                DB_CONNECTION_STRING,
                                emitStableDatabaseSemconv() ? null : "hsqldb:mem:"),
                            satisfies(maybeStable(DB_STATEMENT), val -> val.startsWith("update ")),
                            equalTo(maybeStable(DB_OPERATION), "UPDATE"),
                            equalTo(maybeStable(DB_SQL_TABLE), "JpaCustomer"))));
    clearData();

    customer = findByLastName(repo, "Anonymous").get(0);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("JpaCustomerRepository.findByLastName")
                        .hasKind(SpanKind.INTERNAL)
                        .hasAttributesSatisfyingExactly(
                            equalTo(CodeIncubatingAttributes.CODE_NAMESPACE, repoClassName),
                            equalTo(CodeIncubatingAttributes.CODE_FUNCTION, "findByLastName")),
                span ->
                    span.hasName("SELECT test.JpaCustomer")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), "hsqldb"),
                            equalTo(maybeStable(DB_NAME), "test"),
                            equalTo(DB_USER, emitStableDatabaseSemconv() ? null : "sa"),
                            equalTo(
                                DB_CONNECTION_STRING,
                                emitStableDatabaseSemconv() ? null : "hsqldb:mem:"),
                            satisfies(maybeStable(DB_STATEMENT), val -> val.startsWith("select ")),
                            equalTo(maybeStable(DB_OPERATION), "SELECT"),
                            equalTo(maybeStable(DB_SQL_TABLE), "JpaCustomer"))));
    clearData();

    repo.delete(customer);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("JpaCustomerRepository.delete")
                        .hasKind(SpanKind.INTERNAL)
                        .hasAttributesSatisfyingExactly(
                            equalTo(CodeIncubatingAttributes.CODE_NAMESPACE, repoClassName),
                            equalTo(CodeIncubatingAttributes.CODE_FUNCTION, "delete")),
                span ->
                    span.hasName("SELECT test.JpaCustomer")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), "hsqldb"),
                            equalTo(maybeStable(DB_NAME), "test"),
                            equalTo(DB_USER, emitStableDatabaseSemconv() ? null : "sa"),
                            equalTo(
                                DB_CONNECTION_STRING,
                                emitStableDatabaseSemconv() ? null : "hsqldb:mem:"),
                            satisfies(maybeStable(DB_STATEMENT), val -> val.startsWith("select ")),
                            equalTo(maybeStable(DB_OPERATION), "SELECT"),
                            equalTo(maybeStable(DB_SQL_TABLE), "JpaCustomer")),
                span ->
                    span.hasName("DELETE test.JpaCustomer")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), "hsqldb"),
                            equalTo(maybeStable(DB_NAME), "test"),
                            equalTo(DB_USER, emitStableDatabaseSemconv() ? null : "sa"),
                            equalTo(
                                DB_CONNECTION_STRING,
                                emitStableDatabaseSemconv() ? null : "hsqldb:mem:"),
                            satisfies(maybeStable(DB_STATEMENT), val -> val.startsWith("delete ")),
                            equalTo(maybeStable(DB_OPERATION), "DELETE"),
                            equalTo(maybeStable(DB_SQL_TABLE), "JpaCustomer"))));
  }

  @SuppressWarnings("deprecation") // TODO DB_CONNECTION_STRING deprecation
  @Test
  void testCustomRepositoryMethod() {
    REPOSITORY repo = repository();
    String repoClassName = repositoryClass().getName();
    List<ENTITY> customers = findSpecialCustomers(repo);

    assertTrue(customers.isEmpty());

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("JpaCustomerRepository.findSpecialCustomers")
                        .hasKind(SpanKind.INTERNAL)
                        .hasAttributesSatisfyingExactly(
                            equalTo(CodeIncubatingAttributes.CODE_NAMESPACE, repoClassName),
                            equalTo(
                                CodeIncubatingAttributes.CODE_FUNCTION, "findSpecialCustomers")),
                span ->
                    span.hasName("SELECT test.JpaCustomer")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), "hsqldb"),
                            equalTo(maybeStable(DB_NAME), "test"),
                            equalTo(DB_USER, emitStableDatabaseSemconv() ? null : "sa"),
                            equalTo(
                                DB_CONNECTION_STRING,
                                emitStableDatabaseSemconv() ? null : "hsqldb:mem:"),
                            satisfies(maybeStable(DB_STATEMENT), val -> val.startsWith("select ")),
                            equalTo(maybeStable(DB_OPERATION), "SELECT"),
                            equalTo(maybeStable(DB_SQL_TABLE), "JpaCustomer"))));
  }

  @SuppressWarnings("deprecation") // TODO DB_CONNECTION_STRING deprecation
  @Test
  void testFailedRepositoryMethod() {
    // given
    REPOSITORY repo = repository();
    String repoClassName = repositoryClass().getName();

    String commonLastName = "Smith";
    repo.save(newCustomer("Alice", commonLastName));
    repo.save(newCustomer("Bob", commonLastName));
    clearData();

    // when
    IncorrectResultSizeDataAccessException expectedException =
        catchThrowableOfType(
            () -> findOneByLastName(repo, commonLastName),
            IncorrectResultSizeDataAccessException.class);

    // then
    assertNotNull(expectedException);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("JpaCustomerRepository.findOneByLastName")
                        .hasKind(SpanKind.INTERNAL)
                        .hasStatus(StatusData.error())
                        .hasException(expectedException)
                        .hasAttributesSatisfyingExactly(
                            equalTo(CodeIncubatingAttributes.CODE_NAMESPACE, repoClassName),
                            equalTo(CodeIncubatingAttributes.CODE_FUNCTION, "findOneByLastName")),
                span ->
                    span.hasName("SELECT test.JpaCustomer")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), "hsqldb"),
                            equalTo(maybeStable(DB_NAME), "test"),
                            equalTo(DB_USER, emitStableDatabaseSemconv() ? null : "sa"),
                            equalTo(
                                DB_CONNECTION_STRING,
                                emitStableDatabaseSemconv() ? null : "hsqldb:mem:"),
                            satisfies(maybeStable(DB_STATEMENT), val -> val.startsWith("select ")),
                            equalTo(maybeStable(DB_OPERATION), "SELECT"),
                            equalTo(maybeStable(DB_SQL_TABLE), "JpaCustomer"))));
  }
}
