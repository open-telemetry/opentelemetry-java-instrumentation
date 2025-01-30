/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel.decorators;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;

import com.datastax.oss.driver.api.core.CqlSession;
import com.google.common.collect.ImmutableMap;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerUsingTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.cassandra.CassandraContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class CassandraTest extends AbstractHttpServerUsingTest<ConfigurableApplicationContext> {

  @RegisterExtension
  public static final InstrumentationExtension testing =
      HttpServerInstrumentationExtension.forAgent();

  private ConfigurableApplicationContext appContext;

  @Container
  private static final CassandraContainer cassandra =
      new CassandraContainer("cassandra:3.11.2").withExposedPorts(9042);

  private static String host;

  private static Integer cassandraPort;

  private static CqlSession cqlSession;

  @Override
  protected ConfigurableApplicationContext setupServer() {
    cassandra.start();
    cassandraSetup();

    cassandraPort = cassandra.getFirstMappedPort();
    host = cassandra.getHost();

    SpringApplication app = new SpringApplication(CassandraConfig.class);
    app.setDefaultProperties(
        ImmutableMap.of("cassandra.host", host, "cassandra.port", cassandraPort));
    appContext = app.run();
    return appContext;
  }

  @Override
  protected void stopServer(ConfigurableApplicationContext ctx) {
    ctx.close();
  }

  @Override
  protected String getContextPath() {
    return "";
  }

  @BeforeAll
  protected void setUp() {
    startServer();
  }

  @AfterAll
  protected void cleanUp() {
    cleanupServer();
    cqlSession.close();
    cassandra.stop();
  }

  static void cassandraSetup() {
    cqlSession =
        CqlSession.builder()
            .addContactPoint(cassandra.getContactPoint())
            .withLocalDatacenter(cassandra.getLocalDatacenter())
            .build();

    cqlSession.execute(
        "CREATE KEYSPACE IF NOT EXISTS test WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};");
    cqlSession.execute("CREATE TABLE IF NOT EXISTS test.users (id int PRIMARY KEY, name TEXT);");
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  @Test
  void testCassandra() {
    CamelContext camelContext = appContext.getBean(CamelContext.class);
    ProducerTemplate template = camelContext.createProducerTemplate();

    template.requestBody("direct:input", (Object) null);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasKind(SpanKind.INTERNAL)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(stringKey("camel.uri"), "direct://input")),
                span ->
                    span.hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                stringKey("camel.uri"),
                                "cql://" + host + ":" + cassandraPort + "/test"),
                            equalTo(maybeStable(DB_NAME), "test"),
                            equalTo(
                                maybeStable(DB_STATEMENT),
                                "select * from test.users where id=? ALLOW FILTERING"),
                            equalTo(maybeStable(DB_SYSTEM), "cassandra"))));
  }
}
