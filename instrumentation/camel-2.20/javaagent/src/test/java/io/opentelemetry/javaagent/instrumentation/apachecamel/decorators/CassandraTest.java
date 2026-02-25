/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel.decorators;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.javaagent.instrumentation.apachecamel.ExperimentalTest.experimental;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_SUMMARY;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_TEXT;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_TYPE;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.CASSANDRA;

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
  static final InstrumentationExtension testing = HttpServerInstrumentationExtension.forAgent();

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

    if (emitStableDatabaseSemconv()) {
      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasKind(SpanKind.INTERNAL)
                          .hasNoParent()
                          .hasAttributesSatisfyingExactly(
                              equalTo(stringKey("camel.uri"), experimental("direct://input"))),
                  span ->
                      span.hasName("SELECT test.users")
                          .hasKind(SpanKind.CLIENT)
                          .hasParent(trace.getSpan(0))
                          .hasAttributesSatisfyingExactly(
                              satisfies(NETWORK_TYPE, val -> val.isInstanceOf(String.class)),
                              equalTo(SERVER_ADDRESS, host),
                              equalTo(SERVER_PORT, cassandraPort),
                              satisfies(
                                  NETWORK_PEER_ADDRESS, val -> val.isInstanceOf(String.class)),
                              equalTo(NETWORK_PEER_PORT, cassandraPort),
                              equalTo(DB_SYSTEM_NAME, "cassandra"),
                              equalTo(DB_NAMESPACE, "test"),
                              equalTo(
                                  DB_QUERY_TEXT,
                                  "select * from test.users where id=1 ALLOW FILTERING"),
                              equalTo(DB_QUERY_SUMMARY, "SELECT test.users"))),
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasName("USE test")
                          .hasKind(SpanKind.CLIENT)
                          .hasAttributesSatisfyingExactly(
                              satisfies(NETWORK_TYPE, val -> val.isInstanceOf(String.class)),
                              equalTo(SERVER_ADDRESS, host),
                              equalTo(SERVER_PORT, cassandraPort),
                              satisfies(
                                  NETWORK_PEER_ADDRESS, val -> val.isInstanceOf(String.class)),
                              equalTo(NETWORK_PEER_PORT, cassandraPort),
                              equalTo(DB_SYSTEM_NAME, "cassandra"),
                              equalTo(DB_QUERY_TEXT, "USE test"),
                              equalTo(DB_QUERY_SUMMARY, "USE test"))));
    } else {
      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasKind(SpanKind.INTERNAL)
                          .hasNoParent()
                          .hasAttributesSatisfyingExactly(
                              equalTo(stringKey("camel.uri"), experimental("direct://input"))),
                  // Camel's DB span (less accurate)
                  span ->
                      span.hasName("cql")
                          .hasKind(SpanKind.CLIENT)
                          .hasParent(trace.getSpan(0))
                          .hasAttributesSatisfyingExactly(
                              equalTo(
                                  stringKey("camel.uri"),
                                  experimental("cql://" + host + ":" + cassandraPort + "/test")),
                              equalTo(DB_NAME, "test"),
                              equalTo(
                                  DB_STATEMENT,
                                  "select * from test.users where id=? ALLOW FILTERING"),
                              equalTo(DB_SYSTEM, CASSANDRA)),
                  // Cassandra instrumentation's DB span (more accurate, with connection info)
                  span ->
                      span.hasName("SELECT test.users")
                          .hasKind(SpanKind.CLIENT)
                          .hasParent(trace.getSpan(1))),
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("DB Query").hasKind(SpanKind.CLIENT)));
    }
  }
}
