/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package spring.jpa;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.api.trace.SpanKind.INTERNAL;
import static io.opentelemetry.javaagent.instrumentation.hibernate.ExperimentalTestHelper.HIBERNATE_SESSION_ID;
import static io.opentelemetry.javaagent.instrumentation.hibernate.ExperimentalTestHelper.experimental;
import static io.opentelemetry.javaagent.instrumentation.hibernate.ExperimentalTestHelper.experimentalSatisfies;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_SUMMARY;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_TEXT;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.HSQLDB;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
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

  @SuppressWarnings("deprecation") // using deprecated semconv
  @Test
  void testCrud() {
    Customer customer = new Customer("Bob", "Anonymous");

    assertThat(customer.getId()).isNull();
    assertThat(testing.runWithSpan("parent", () -> repo.findAll().iterator().hasNext())).isFalse();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("parent")
                        .hasKind(INTERNAL)
                        .hasNoParent()
                        .hasTotalAttributeCount(0),
                span ->
                    span.hasName("select spring.jpa.Customer")
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            experimentalSatisfies(
                                HIBERNATE_SESSION_ID,
                                val -> assertThat(val).isInstanceOf(String.class))),
                span ->
                    span.hasName("select Customer")
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, HSQLDB),
                            equalTo(DB_NAMESPACE, "test"),
                            satisfies(
                                DB_QUERY_TEXT,
                                val ->
                                    val.matches(
                                        "select ([^.]+)\\.id([^,]*),([^.]+)\\.firstName([^,]*),([^.]+)\\.lastName(.*)from Customer(.*)")),
                            equalTo(DB_QUERY_SUMMARY, "select Customer")),
                span ->
                    span.hasName("Transaction.commit")
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                HIBERNATE_SESSION_ID,
                                experimental(
                                    trace.getSpan(1).getAttributes().get(HIBERNATE_SESSION_ID))))));

    testing.clearData();

    testing.runWithSpan("parent", () -> repo.save(customer));
    Long savedId = customer.getId();

    assertThat(customer.getId()).isNotNull();
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("parent")
                        .hasKind(INTERNAL)
                        .hasNoParent()
                        .hasTotalAttributeCount(0),
                span ->
                    span.hasName("Session.persist spring.jpa.Customer")
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            experimentalSatisfies(
                                HIBERNATE_SESSION_ID,
                                val -> assertThat(val).isInstanceOf(String.class))),
                span ->
                    span.hasName("call Customer_SEQ")
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, HSQLDB),
                            equalTo(DB_NAMESPACE, "test"),
                            equalTo(DB_QUERY_TEXT, "call next value for Customer_SEQ"),
                            equalTo(DB_QUERY_SUMMARY, "call Customer_SEQ")),
                span ->
                    span.hasName("Transaction.commit")
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                HIBERNATE_SESSION_ID,
                                experimental(
                                    trace.getSpan(1).getAttributes().get(HIBERNATE_SESSION_ID)))),
                span ->
                    span.hasName("insert Customer")
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(3))
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, HSQLDB),
                            equalTo(DB_NAMESPACE, "test"),
                            satisfies(
                                DB_QUERY_TEXT,
                                val ->
                                    val.matches("insert into Customer \\(.*\\) values \\(.*\\)")),
                            equalTo(DB_QUERY_SUMMARY, "insert Customer"))));

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
                        .hasTotalAttributeCount(0),
                span ->
                    span.hasName("Session.merge spring.jpa.Customer")
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            experimentalSatisfies(
                                HIBERNATE_SESSION_ID,
                                val -> assertThat(val).isInstanceOf(String.class))),
                span ->
                    span.hasName("select Customer")
                        .hasKind(CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, HSQLDB),
                            equalTo(DB_NAMESPACE, "test"),
                            satisfies(
                                DB_QUERY_TEXT,
                                val ->
                                    val.matches(
                                        "select ([^.]+)\\.id([^,]*),([^.]+)\\.firstName([^,]*),([^.]+)\\.lastName (.*)from Customer (.*)where ([^.]+)\\.id( ?)=( ?)\\?")),
                            equalTo(DB_QUERY_SUMMARY, "select Customer")),
                span ->
                    span.hasName("Transaction.commit")
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                HIBERNATE_SESSION_ID,
                                experimental(
                                    trace.getSpan(1).getAttributes().get(HIBERNATE_SESSION_ID)))),
                span ->
                    span.hasName("update Customer")
                        .hasKind(CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, HSQLDB),
                            equalTo(DB_NAMESPACE, "test"),
                            satisfies(
                                DB_QUERY_TEXT,
                                val ->
                                    val.matches(
                                        "update Customer set firstName=\\?,(.*)lastName=\\? where id=\\?")),
                            equalTo(DB_QUERY_SUMMARY, "update Customer"))));
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
                        .hasTotalAttributeCount(0),
                span ->
                    span.satisfies(
                            spanData ->
                                assertThat(spanData.getName())
                                    .isIn(asList("select spring.jpa.Customer", "hibernate")))
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            experimentalSatisfies(
                                HIBERNATE_SESSION_ID,
                                val -> assertThat(val).isInstanceOf(String.class))),
                span ->
                    span.hasName("select Customer")
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, HSQLDB),
                            equalTo(DB_NAMESPACE, "test"),
                            satisfies(
                                DB_QUERY_TEXT,
                                val ->
                                    val.matches(
                                        "select ([^.]+)\\.id([^,]*),([^.]+)\\.firstName([^,]*),([^.]+)\\.lastName (.*)from Customer (.*)(where ([^.]+)\\.lastName( ?)=( ?)\\?|)")),
                            equalTo(DB_QUERY_SUMMARY, "select Customer"))));
    testing.clearData();

    testing.runWithSpan("parent", () -> repo.delete(anonymousCustomer));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("parent")
                        .hasKind(INTERNAL)
                        .hasNoParent()
                        .hasTotalAttributeCount(0),
                span ->
                    span.satisfies(
                            spanData ->
                                assertThat(spanData.getName())
                                    .matches("Session.(get|find) spring.jpa.Customer"))
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            experimentalSatisfies(
                                HIBERNATE_SESSION_ID,
                                val -> assertThat(val).isInstanceOf(String.class))),
                span ->
                    span.hasName("select Customer")
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, HSQLDB),
                            equalTo(DB_NAMESPACE, "test"),
                            satisfies(
                                DB_QUERY_TEXT,
                                val ->
                                    val.matches(
                                        "select ([^.]+)\\.id([^,]*),([^.]+)\\.firstName([^,]*),([^.]+)\\.lastName (.*)from Customer (.*)(where ([^.]+)\\.lastName( ?)=( ?)\\?|)")),
                            equalTo(DB_QUERY_SUMMARY, "select Customer")),
                span ->
                    span.hasName("Session.merge spring.jpa.Customer")
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            experimentalSatisfies(
                                HIBERNATE_SESSION_ID,
                                val -> assertThat(val).isInstanceOf(String.class))),
                span ->
                    span.hasName("Session.remove spring.jpa.Customer")
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            experimentalSatisfies(
                                HIBERNATE_SESSION_ID,
                                val -> assertThat(val).isInstanceOf(String.class))),
                span ->
                    span.hasName("Transaction.commit")
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            experimentalSatisfies(
                                HIBERNATE_SESSION_ID,
                                val -> assertThat(val).isInstanceOf(String.class))),
                span ->
                    span.hasName("delete Customer")
                        .hasKind(CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, HSQLDB),
                            equalTo(DB_NAMESPACE, "test"),
                            equalTo(DB_QUERY_TEXT, "delete from Customer where id=?"),
                            equalTo(DB_QUERY_SUMMARY, "delete Customer"))));
  }
}
