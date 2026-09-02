/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.influxdb.v2_4;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_QUERY_SUMMARY;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.INFLUXDB;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.Query;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.GenericContainer;

// using deprecated InfluxDB and semconv APIs
@SuppressWarnings("deprecation")
@TestInstance(Lifecycle.PER_CLASS)
class InfluxDbQuerySanitizationDisabledTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension
  private static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private static final GenericContainer<?> influxDbServer =
      new GenericContainer<>("influxdb:1.8.10-alpine").withExposedPorts(8086);

  private static InfluxDB influxDb;

  private static final String DATABASE_NAME = "mydb";

  private static String host;

  private static int port;

  @BeforeAll
  void setup() {
    cleanup.deferAfterAll(influxDbServer::stop);
    influxDbServer.start();
    port = influxDbServer.getMappedPort(8086);
    host = influxDbServer.getHost();
    String serverUrl = "http://" + host + ":" + port + "/";
    influxDb = InfluxDBFactory.connect(serverUrl, "root", "root");
    cleanup.deferAfterAll(influxDb);
    influxDb.createDatabase(DATABASE_NAME);
    cleanup.deferAfterAll(() -> influxDb.deleteDatabase(DATABASE_NAME));
  }

  @Test
  void testQueryIsNotSanitized() {
    Query query = new Query("SELECT * FROM cpu_load where test1 = 'influxDb'", DATABASE_NAME);
    influxDb.query(query, MILLISECONDS);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(
                            emitStableDatabaseSemconv()
                                ? "SELECT cpu_load"
                                : "SELECT " + DATABASE_NAME)
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), INFLUXDB),
                            equalTo(maybeStable(DB_NAME), DATABASE_NAME),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(
                                maybeStable(DB_OPERATION),
                                emitStableDatabaseSemconv() ? null : "SELECT"),
                            equalTo(
                                maybeStable(DB_STATEMENT),
                                "SELECT * FROM cpu_load where test1 = 'influxDb'"),
                            equalTo(
                                DB_QUERY_SUMMARY,
                                emitStableDatabaseSemconv() ? "SELECT cpu_load" : null))));
  }
}
