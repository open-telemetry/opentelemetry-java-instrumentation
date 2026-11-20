/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package spring.jpa;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.api.trace.SpanKind.INTERNAL;
import static io.opentelemetry.javaagent.instrumentation.hibernate.ExperimentalTestHelper.experimental;
import static io.opentelemetry.javaagent.instrumentation.hibernate.ExperimentalTestHelper.experimentalSatisfies;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_SUMMARY;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_TEXT;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.HSQLDB;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.regex.Pattern;
import org.hibernate.Version;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class SpringJpaTest {

  @RegisterExtension
  protected static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private final AnnotationConfigApplicationContext context =
      new AnnotationConfigApplicationContext(PersistenceConfig.class);
  private final CustomerRepository repo = context.getBean(CustomerRepository.class);

  @AfterEach
  void closeContext() {
    context.close();
  }

  @SuppressWarnings("deprecation") // TODO DB_CONNECTION_STRING deprecation
  @Test
  void testCrud() {
    String version = Version.getVersionString();
    boolean isHibernate4 = version.startsWith("4.");
    boolean isLatestDep = version.startsWith("5.0");

    Customer customer = new Customer("Bob", "Anonymous");
    customer.setId(null);

    boolean result = testing.runWithSpan("parent", () -> repo.findAll().iterator().hasNext());

    assertThat(result).isFalse();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("parent")
                        .hasKind(INTERNAL)
                        .hasNoParent()
                        .hasTotalAttributeCount(0),
                span ->
                    span.hasName("select Customer")
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            experimentalSatisfies(
                                stringKey("hibernate.session_id"),
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
                                        Pattern.compile(
                                            "select ([^.]+).id([^,]*), ([^.]+).firstName([^,]*), ([^.]+).lastName(.*)from Customer(.*)"))),
                            equalTo(DB_QUERY_SUMMARY, "select Customer")),
                span ->
                    span.hasName("Transaction.commit")
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                stringKey("hibernate.session_id"),
                                experimental(
                                    trace
                                        .getSpan(1)
                                        .getAttributes()
                                        .get(stringKey("hibernate.session_id")))))));
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
                        .hasTotalAttributeCount(0),
                span ->
                    span.hasName("Session.persist spring.jpa.Customer")
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            experimentalSatisfies(
                                stringKey("hibernate.session_id"),
                                val -> assertThat(val).isInstanceOf(String.class))),
                span ->
                    span.hasName("insert Customer")
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, HSQLDB),
                            equalTo(DB_NAMESPACE, "test"),
                            satisfies(
                                DB_QUERY_TEXT,
                                val ->
                                    val.matches(
                                        Pattern.compile(
                                            "insert into Customer (.*) values \\(.*, \\?, \\?\\)"))),
                            equalTo(DB_QUERY_SUMMARY, "insert Customer")),
                span ->
                    span.hasName("Transaction.commit")
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                stringKey("hibernate.session_id"),
                                experimental(
                                    trace
                                        .getSpan(1)
                                        .getAttributes()
                                        .get(stringKey("hibernate.session_id"))))));
          } else {
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
                                stringKey("hibernate.session_id"),
                                val -> assertThat(val).isInstanceOf(String.class))),
                span ->
                    span.hasName("call hibernate_sequence")
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(1))
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, HSQLDB),
                            equalTo(DB_NAMESPACE, "test"),
                            equalTo(DB_QUERY_TEXT, "call next value for hibernate_sequence"),
                            equalTo(DB_QUERY_SUMMARY, "call hibernate_sequence")),
                span ->
                    span.hasName("Transaction.commit")
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                stringKey("hibernate.session_id"),
                                experimental(
                                    trace
                                        .getSpan(1)
                                        .getAttributes()
                                        .get(stringKey("hibernate.session_id"))))),
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
                                    val.matches(
                                        Pattern.compile(
                                            "insert into Customer (.*) values \\(.* \\?, \\?\\)"))),
                            equalTo(DB_QUERY_SUMMARY, "insert Customer")));
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
                        .hasTotalAttributeCount(0),
                span ->
                    span.hasName("Session.merge spring.jpa.Customer")
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            experimentalSatisfies(
                                stringKey("hibernate.session_id"),
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
                                        Pattern.compile(
                                            "select ([^.]+).id([^,]*), ([^.]+).firstName([^,]*), ([^.]+).lastName (.*)from Customer (.*)where ([^.]+).id=\\?"))),
                            equalTo(DB_QUERY_SUMMARY, "select Customer")),
                span ->
                    span.hasName("Transaction.commit")
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                stringKey("hibernate.session_id"),
                                experimental(
                                    trace
                                        .getSpan(1)
                                        .getAttributes()
                                        .get(stringKey("hibernate.session_id"))))),
                span ->
                    span.hasName("update Customer")
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(3))
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, HSQLDB),
                            equalTo(DB_NAMESPACE, "test"),
                            equalTo(
                                DB_QUERY_TEXT,
                                "update Customer set firstName=?, lastName=? where id=?"),
                            equalTo(DB_QUERY_SUMMARY, "update Customer"))));
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
                        .hasTotalAttributeCount(0),
                span ->
                    span.hasName("select Customer")
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            experimentalSatisfies(
                                stringKey("hibernate.session_id"),
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
                                        Pattern.compile(
                                            "select ([^.]+).id([^,]*), ([^.]+).firstName([^,]*), ([^.]+).lastName (.*)from Customer (.*)(where ([^.]+).lastName=\\?)"))),
                            equalTo(DB_QUERY_SUMMARY, "select Customer"))));
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
                        .hasTotalAttributeCount(0),
                span ->
                    span.hasName("Session.merge spring.jpa.Customer")
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            experimentalSatisfies(
                                stringKey("hibernate.session_id"),
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
                                        Pattern.compile(
                                            "select ([^.]+).id([^,]*), ([^.]+).firstName([^,]*), ([^.]+).lastName (.*)from Customer (.*)where ([^.]+).id=\\?"))),
                            equalTo(DB_QUERY_SUMMARY, "select Customer")),
                span ->
                    span.hasName("Session.delete spring.jpa.Customer")
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            experimentalSatisfies(
                                stringKey("hibernate.session_id"),
                                val -> assertThat(val).isInstanceOf(String.class))),
                span ->
                    span.hasName("Transaction.commit")
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            experimentalSatisfies(
                                stringKey("hibernate.session_id"),
                                val -> assertThat(val).isInstanceOf(String.class))),
                span ->
                    span.hasName("delete Customer")
                        .hasKind(CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, HSQLDB),
                            equalTo(DB_NAMESPACE, "test"),
                            equalTo(DB_QUERY_TEXT, "delete from Customer where id=?"),
                            equalTo(DB_QUERY_SUMMARY, "delete Customer")));

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
                            experimentalSatisfies(
                                stringKey("hibernate.session_id"),
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
                                        Pattern.compile(
                                            "select ([^.]+).id([^,]*), ([^.]+).firstName([^,]*), ([^.]+).lastName (.*)from Customer (.*)where ([^.]+).id=\\?"))),
                            equalTo(DB_QUERY_SUMMARY, "select Customer")),
                span ->
                    span.hasName("Session.merge spring.jpa.Customer")
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            experimentalSatisfies(
                                stringKey("hibernate.session_id"),
                                val -> assertThat(val).isInstanceOf(String.class))),
                span ->
                    span.hasName("Session.delete spring.jpa.Customer")
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            experimentalSatisfies(
                                stringKey("hibernate.session_id"),
                                val -> assertThat(val).isInstanceOf(String.class))),
                span ->
                    span.hasName("Transaction.commit")
                        .hasKind(INTERNAL)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            experimentalSatisfies(
                                stringKey("hibernate.session_id"),
                                val -> assertThat(val).isInstanceOf(String.class))),
                span ->
                    span.hasName("delete Customer")
                        .hasKind(CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, HSQLDB),
                            equalTo(DB_NAMESPACE, "test"),
                            equalTo(DB_QUERY_TEXT, "delete from Customer where id=?"),
                            equalTo(DB_QUERY_SUMMARY, "delete Customer")));
          }
        });
  }
}
