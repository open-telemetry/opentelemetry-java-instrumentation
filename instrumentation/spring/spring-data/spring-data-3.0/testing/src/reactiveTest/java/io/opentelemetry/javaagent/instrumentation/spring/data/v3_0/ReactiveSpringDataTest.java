/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.data.v3_0;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_CONNECTION_STRING;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SQL_TABLE;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_USER;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.javaagent.instrumentation.spring.data.v3_0.repository.CustomerRepository;
import io.opentelemetry.javaagent.instrumentation.spring.data.v3_0.repository.PersistenceConfig;
import io.opentelemetry.semconv.incubating.CodeIncubatingAttributes;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class ReactiveSpringDataTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static ConfigurableApplicationContext applicationContext;
  private static CustomerRepository customerRepository;

  @BeforeAll
  static void setUp() {
    applicationContext = new AnnotationConfigApplicationContext(PersistenceConfig.class);
    customerRepository = applicationContext.getBean(CustomerRepository.class);
  }

  @AfterAll
  static void cleanUp() {
    applicationContext.close();
  }

  @SuppressWarnings("deprecation") // TODO DbIncubatingAttributes.DB_CONNECTION_STRING deprecation
  @Test
  void testFindAll() {
    long count =
        testing
            .runWithSpan("parent", () -> customerRepository.findAll())
            .count()
            .block(Duration.ofSeconds(30));
    assertThat(count).isEqualTo(1);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL),
                span ->
                    span.hasName("CustomerRepository.findAll")
                        .hasKind(SpanKind.INTERNAL)
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                CodeIncubatingAttributes.CODE_NAMESPACE,
                                CustomerRepository.class.getName()),
                            equalTo(CodeIncubatingAttributes.CODE_FUNCTION, "findAll")),
                span ->
                    span.hasName("SELECT db.CUSTOMER")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(1))
                        // assert that this span ends before its parent span
                        .satisfies(
                            spanData ->
                                assertThat(spanData.getEndEpochNanos())
                                    .isLessThanOrEqualTo(trace.getSpan(1).getEndEpochNanos()))
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM, "h2"),
                            equalTo(DB_NAME, "db"),
                            equalTo(DB_USER, "sa"),
                            equalTo(DB_STATEMENT, "SELECT CUSTOMER.* FROM CUSTOMER"),
                            equalTo(DB_OPERATION, "SELECT"),
                            equalTo(DB_SQL_TABLE, "CUSTOMER"),
                            equalTo(DB_CONNECTION_STRING, "h2:mem://localhost"),
                            equalTo(SERVER_ADDRESS, "localhost"))));
  }
}
