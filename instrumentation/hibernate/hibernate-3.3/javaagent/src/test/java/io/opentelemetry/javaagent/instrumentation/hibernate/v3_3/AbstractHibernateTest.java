/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate.v3_3;

import static io.opentelemetry.javaagent.instrumentation.hibernate.ExperimentalTestHelper.HIBERNATE_SESSION_ID;
import static io.opentelemetry.javaagent.instrumentation.hibernate.ExperimentalTestHelper.experimental;
import static io.opentelemetry.javaagent.instrumentation.hibernate.ExperimentalTestHelper.experimentalSatisfies;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_SUMMARY;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_TEXT;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.AnnotationConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractHibernateTest {
  @RegisterExtension
  protected static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  protected SessionFactory sessionFactory;
  protected List<Value> prepopulated;

  @BeforeAll
  void setUp() {
    sessionFactory = new AnnotationConfiguration().configure().buildSessionFactory();

    // Pre-populate the DB, so delete/update can be tested.
    Session writer = sessionFactory.openSession();
    writer.beginTransaction();
    prepopulated = new ArrayList<>();
    for (int i = 0; i < 2; i++) {
      prepopulated.add(new Value("Hello :) " + i));
      writer.save(prepopulated.get(i));
    }
    writer.getTransaction().commit();
    writer.close();
  }

  @AfterAll
  void cleanUp() {
    if (sessionFactory != null) {
      sessionFactory.close();
    }
  }

  @SuppressWarnings("deprecation") // TODO DB_CONNECTION_STRING deprecation
  static void assertClientSpan(SpanDataAssert span, SpanData parent) {
    span.hasKind(SpanKind.CLIENT)
        .hasParent(parent)
        .hasAttributesSatisfyingExactly(
            equalTo(DB_SYSTEM_NAME, "h2database"),
            equalTo(DB_NAMESPACE, "db1"),
            satisfies(DB_QUERY_TEXT, val -> val.isInstanceOf(String.class)),
            satisfies(
                DB_QUERY_SUMMARY,
                val -> {
                  val.isInstanceOf(String.class);
                }),
            satisfies(
                DB_OPERATION_NAME,
                val -> {
                  val.isNull();
                }));
  }

  @SuppressWarnings("deprecation") // TODO DB_CONNECTION_STRING deprecation
  static void assertClientSpan(SpanDataAssert span, SpanData parent, String verb) {
    span.hasName(verb + " Value")
        .hasKind(SpanKind.CLIENT)
        .hasParent(parent)
        .hasAttributesSatisfyingExactly(
            equalTo(DB_SYSTEM_NAME, "h2database"),
            equalTo(DB_NAMESPACE, "db1"),
            satisfies(DB_QUERY_TEXT, val -> val.startsWithIgnoringCase(verb)),
            equalTo(DB_QUERY_SUMMARY, verb + " Value"));
  }

  static SpanDataAssert assertSessionSpan(SpanDataAssert span, SpanData parent, String spanName) {
    return span.hasName(spanName)
        .hasKind(SpanKind.INTERNAL)
        .hasParent(parent)
        .hasAttributesSatisfyingExactly(
            experimentalSatisfies(
                HIBERNATE_SESSION_ID, val -> assertThat(val).isInstanceOf(String.class)));
  }

  static void assertSpanWithSessionId(
      SpanDataAssert span, SpanData parent, String spanName, String sessionId) {
    span.hasName(spanName)
        .hasKind(SpanKind.INTERNAL)
        .hasParent(parent)
        .hasAttributesSatisfyingExactly(equalTo(HIBERNATE_SESSION_ID, experimental(sessionId)));
  }
}
