/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package spring.jpa;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.api.trace.SpanKind.INTERNAL;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.semconv.SemanticAttributes;
import java.util.regex.Pattern;
import org.hibernate.Version;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class SpringJpaTest {

  @RegisterExtension
  protected static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  AnnotationConfigApplicationContext context =
      new AnnotationConfigApplicationContext(PersistenceConfig.class);
  CustomerRepository repo = context.getBean(CustomerRepository.class);

  @Test
  void testCrud() {
    String version = Version.getVersionString();
    boolean isHibernate4 = version.startsWith("4.");
    boolean isLatestDep = version.startsWith("5.0");

    Customer customer = new Customer("Bob", "Anonymous");
    customer.setId(null);

    boolean result = testing.runWithSpan("parent", () -> repo.findAll().iterator().hasNext());

    assertThat(result).isEqualTo(false);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("parent")
                        .hasKind(INTERNAL)
                        .hasNoParent()
                        .hasAttributes(Attributes.empty()),
                span ->
                    span.hasName("SELECT Customer")
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            satisfies(
                                stringKey("hibernate.session_id"),
                                val -> val.isInstanceOf(String.class))),
                span ->
                    span.hasName("SELECT test.Customer")
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.DB_SYSTEM, "hsqldb"),
                            equalTo(SemanticAttributes.DB_NAME, "test"),
                            equalTo(SemanticAttributes.DB_USER, "sa"),
                            equalTo(SemanticAttributes.DB_CONNECTION_STRING, "hsqldb:mem:"),
                            satisfies(
                                SemanticAttributes.DB_STATEMENT,
                                val ->
                                    val.matches(
                                        Pattern.compile(
                                            "select ([^.]+).id([^,]*), ([^.]+).firstName([^,]*), ([^.]+).lastName(.*)from Customer(.*)"))),
                            equalTo(SemanticAttributes.DB_OPERATION, "SELECT"),
                            equalTo(SemanticAttributes.DB_SQL_TABLE, "Customer")),
                span ->
                    span.hasName("Transaction.commit")
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                stringKey("hibernate.session_id"),
                                trace
                                    .getSpan(1)
                                    .getAttributes()
                                    .get(stringKey("hibernate.session_id"))))));
    testing.clearData();

    testing.runWithSpan(
        "parent",
        () -> {
          repo.save(customer);
        });

    assertThat(customer.getId()).isNotNull();

    testing.waitAndAssertTraces(
        trace -> {
          if (isHibernate4) {
            trace.hasSpansSatisfyingExactly(
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
                                stringKey("hibernate.session_id"),
                                val -> val.isInstanceOf(String.class))),
                span ->
                    span.hasName("INSERT test.Customer")
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.DB_SYSTEM, "hsqldb"),
                            equalTo(SemanticAttributes.DB_NAME, "test"),
                            equalTo(SemanticAttributes.DB_USER, "sa"),
                            equalTo(SemanticAttributes.DB_CONNECTION_STRING, "hsqldb:mem:"),
                            satisfies(
                                SemanticAttributes.DB_STATEMENT,
                                val ->
                                    val.matches(
                                        Pattern.compile(
                                            "insert into Customer (.*) values \\(.*, \\?, \\?\\)"))),
                            equalTo(SemanticAttributes.DB_OPERATION, "INSERT"),
                            equalTo(SemanticAttributes.DB_SQL_TABLE, "Customer")),
                span ->
                    span.hasName("Transaction.commit")
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                stringKey("hibernate.session_id"),
                                trace
                                    .getSpan(1)
                                    .getAttributes()
                                    .get(stringKey("hibernate.session_id")))));
          } else {
            trace.hasSpansSatisfyingExactly(
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
                                stringKey("hibernate.session_id"),
                                val -> val.isInstanceOf(String.class))),
                span ->
                    span.hasName("CALL test")
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.DB_SYSTEM, "hsqldb"),
                            equalTo(SemanticAttributes.DB_NAME, "test"),
                            equalTo(SemanticAttributes.DB_USER, "sa"),
                            equalTo(
                                SemanticAttributes.DB_STATEMENT,
                                "call next value for hibernate_sequence"),
                            equalTo(SemanticAttributes.DB_CONNECTION_STRING, "hsqldb:mem:"),
                            equalTo(SemanticAttributes.DB_OPERATION, "CALL")),
                span ->
                    span.hasName("Transaction.commit")
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                stringKey("hibernate.session_id"),
                                trace
                                    .getSpan(1)
                                    .getAttributes()
                                    .get(stringKey("hibernate.session_id")))),
                span ->
                    span.hasName("INSERT test.Customer")
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(3))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.DB_SYSTEM, "hsqldb"),
                            equalTo(SemanticAttributes.DB_NAME, "test"),
                            equalTo(SemanticAttributes.DB_USER, "sa"),
                            equalTo(SemanticAttributes.DB_CONNECTION_STRING, "hsqldb:mem:"),
                            satisfies(
                                SemanticAttributes.DB_STATEMENT,
                                val ->
                                    val.matches(
                                        Pattern.compile(
                                            "insert into Customer (.*) values \\(.* \\?, \\?\\)"))),
                            equalTo(SemanticAttributes.DB_OPERATION, "INSERT"),
                            equalTo(SemanticAttributes.DB_SQL_TABLE, "Customer")));
          }
        });
    testing.clearData();

    customer.setFirstName("Bill");

    testing.runWithSpan(
        "parent",
        () -> {
          repo.save(customer);
        });

    Long savedId = customer.getId();

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
                                stringKey("hibernate.session_id"),
                                val -> val.isInstanceOf(String.class))),
                span ->
                    span.hasName("SELECT test.Customer")
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.DB_SYSTEM, "hsqldb"),
                            equalTo(SemanticAttributes.DB_NAME, "test"),
                            equalTo(SemanticAttributes.DB_USER, "sa"),
                            equalTo(SemanticAttributes.DB_CONNECTION_STRING, "hsqldb:mem:"),
                            satisfies(
                                SemanticAttributes.DB_STATEMENT,
                                val ->
                                    val.matches(
                                        Pattern.compile(
                                            "select ([^.]+).id([^,]*), ([^.]+).firstName([^,]*), ([^.]+).lastName (.*)from Customer (.*)where ([^.]+).id=\\?"))),
                            equalTo(SemanticAttributes.DB_OPERATION, "SELECT"),
                            equalTo(SemanticAttributes.DB_SQL_TABLE, "Customer")),
                span ->
                    span.hasName("Transaction.commit")
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                stringKey("hibernate.session_id"),
                                trace
                                    .getSpan(1)
                                    .getAttributes()
                                    .get(stringKey("hibernate.session_id")))),
                span ->
                    span.hasName("UPDATE test.Customer")
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(3))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.DB_SYSTEM, "hsqldb"),
                            equalTo(SemanticAttributes.DB_NAME, "test"),
                            equalTo(SemanticAttributes.DB_USER, "sa"),
                            equalTo(SemanticAttributes.DB_CONNECTION_STRING, "hsqldb:mem:"),
                            equalTo(
                                SemanticAttributes.DB_STATEMENT,
                                "update Customer set firstName=?, lastName=? where id=?"),
                            equalTo(SemanticAttributes.DB_OPERATION, "UPDATE"),
                            equalTo(SemanticAttributes.DB_SQL_TABLE, "Customer"))));
    testing.clearData();

    Customer foundCustomer =
        testing.runWithSpan("parent", () -> repo.findByLastName("Anonymous").get(0));

    assertThat(foundCustomer.getId()).isEqualTo(savedId);
    assertThat(foundCustomer.getFirstName()).isEqualTo("Bill");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("parent")
                        .hasKind(INTERNAL)
                        .hasNoParent()
                        .hasAttributes(Attributes.empty()),
                span ->
                    span.hasName("SELECT Customer")
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            satisfies(
                                stringKey("hibernate.session_id"),
                                val -> val.isInstanceOf(String.class))),
                span ->
                    span.hasName("SELECT test.Customer")
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.DB_SYSTEM, "hsqldb"),
                            equalTo(SemanticAttributes.DB_NAME, "test"),
                            equalTo(SemanticAttributes.DB_USER, "sa"),
                            equalTo(SemanticAttributes.DB_CONNECTION_STRING, "hsqldb:mem:"),
                            satisfies(
                                SemanticAttributes.DB_STATEMENT,
                                val ->
                                    val.matches(
                                        Pattern.compile(
                                            "select ([^.]+).id([^,]*), ([^.]+).firstName([^,]*), ([^.]+).lastName (.*)from Customer (.*)(where ([^.]+).lastName=\\?)"))),
                            equalTo(SemanticAttributes.DB_OPERATION, "SELECT"),
                            equalTo(SemanticAttributes.DB_SQL_TABLE, "Customer"))));
    testing.clearData();

    testing.runWithSpan("parent", () -> repo.delete(foundCustomer));

    testing.waitAndAssertTraces(
        trace -> {
          if (isHibernate4) {
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
                                stringKey("hibernate.session_id"),
                                val -> val.isInstanceOf(String.class))),
                span ->
                    span.hasName("SELECT test.Customer")
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.DB_SYSTEM, "hsqldb"),
                            equalTo(SemanticAttributes.DB_NAME, "test"),
                            equalTo(SemanticAttributes.DB_USER, "sa"),
                            equalTo(SemanticAttributes.DB_CONNECTION_STRING, "hsqldb:mem:"),
                            satisfies(
                                SemanticAttributes.DB_STATEMENT,
                                val ->
                                    val.matches(
                                        Pattern.compile(
                                            "select ([^.]+).id([^,]*), ([^.]+).firstName([^,]*), ([^.]+).lastName (.*)from Customer (.*)where ([^.]+).id=\\?"))),
                            equalTo(SemanticAttributes.DB_OPERATION, "SELECT"),
                            equalTo(SemanticAttributes.DB_SQL_TABLE, "Customer")),
                span ->
                    span.hasName("Session.delete spring.jpa.Customer")
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            satisfies(
                                stringKey("hibernate.session_id"),
                                val -> val.isInstanceOf(String.class))),
                span ->
                    span.hasName("Transaction.commit")
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            satisfies(
                                stringKey("hibernate.session_id"),
                                val -> val.isInstanceOf(String.class))),
                span ->
                    span.hasName("DELETE test.Customer")
                        .hasKind(CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.DB_SYSTEM, "hsqldb"),
                            equalTo(SemanticAttributes.DB_NAME, "test"),
                            equalTo(SemanticAttributes.DB_USER, "sa"),
                            equalTo(SemanticAttributes.DB_CONNECTION_STRING, "hsqldb:mem:"),
                            equalTo(
                                SemanticAttributes.DB_STATEMENT, "delete from Customer where id=?"),
                            equalTo(SemanticAttributes.DB_OPERATION, "DELETE"),
                            equalTo(SemanticAttributes.DB_SQL_TABLE, "Customer")));

          } else {
            String findAction;
            if (isLatestDep) {
              findAction = "get";
            } else {
              findAction = "find";
            }

            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(INTERNAL).hasNoParent(),
                span ->
                    span.hasName("Session." + findAction + " spring.jpa.Customer")
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            satisfies(
                                stringKey("hibernate.session_id"),
                                val -> val.isInstanceOf(String.class))),
                span ->
                    span.hasName("SELECT test.Customer")
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.DB_SYSTEM, "hsqldb"),
                            equalTo(SemanticAttributes.DB_NAME, "test"),
                            equalTo(SemanticAttributes.DB_USER, "sa"),
                            equalTo(SemanticAttributes.DB_CONNECTION_STRING, "hsqldb:mem:"),
                            satisfies(
                                SemanticAttributes.DB_STATEMENT,
                                val ->
                                    val.matches(
                                        Pattern.compile(
                                            "select ([^.]+).id([^,]*), ([^.]+).firstName([^,]*), ([^.]+).lastName (.*)from Customer (.*)where ([^.]+).id=\\?"))),
                            equalTo(SemanticAttributes.DB_OPERATION, "SELECT"),
                            equalTo(SemanticAttributes.DB_SQL_TABLE, "Customer")),
                span ->
                    span.hasName("Session.merge spring.jpa.Customer")
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            satisfies(
                                stringKey("hibernate.session_id"),
                                val -> val.isInstanceOf(String.class))),
                span ->
                    span.hasName("Session.delete spring.jpa.Customer")
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            satisfies(
                                stringKey("hibernate.session_id"),
                                val -> val.isInstanceOf(String.class))),
                span ->
                    span.hasName("Transaction.commit")
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            satisfies(
                                stringKey("hibernate.session_id"),
                                val -> val.isInstanceOf(String.class))),
                span ->
                    span.hasName("DELETE test.Customer")
                        .hasKind(CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.DB_SYSTEM, "hsqldb"),
                            equalTo(SemanticAttributes.DB_NAME, "test"),
                            equalTo(SemanticAttributes.DB_USER, "sa"),
                            equalTo(SemanticAttributes.DB_CONNECTION_STRING, "hsqldb:mem:"),
                            equalTo(
                                SemanticAttributes.DB_STATEMENT, "delete from Customer where id=?"),
                            equalTo(SemanticAttributes.DB_OPERATION, "DELETE"),
                            equalTo(SemanticAttributes.DB_SQL_TABLE, "Customer")));
          }
        });
  }
}
