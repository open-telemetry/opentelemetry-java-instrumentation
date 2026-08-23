/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.geode.v1_4;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.instrumentation.testing.junit.db.DbClientMetricsTestUtil.assertDurationMetric;
import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.instrumentation.testing.util.TestLatestDeps.testLatestDeps;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.DbAttributes.DB_COLLECTION_NAME;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.GEODE;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.stream.Stream;
import org.apache.geode.cache.Region;
import org.apache.geode.cache.client.ClientCache;
import org.apache.geode.cache.client.ClientCacheFactory;
import org.apache.geode.cache.client.ClientRegionFactory;
import org.apache.geode.cache.client.ClientRegionShortcut;
import org.apache.geode.cache.query.QueryException;
import org.apache.geode.cache.query.SelectResults;
import org.apache.geode.pdx.PdxReader;
import org.apache.geode.pdx.PdxSerializable;
import org.apache.geode.pdx.PdxWriter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.utility.MountableFile;

@SuppressWarnings("deprecation") // using deprecated semconv
class PutGetTest {
  private static final int GEODE_PORT = 40404;

  private static final GenericContainer<?> geodeServer =
      createGeodeServer()
          .withExposedPorts(GEODE_PORT)
          .withCopyFileToContainer(
              MountableFile.forClasspathResource("geode-cache.xml"), "/geode-cache.xml")
          .withCommand(
              "sh",
              "-c",
              "gfsh -e \"start server --name=test-server"
                  + " --cache-xml-file=/geode-cache.xml"
                  + " --max-heap=256m\""
                  + " && tail -F /test-server/test-server.log");

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension
  private static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private static ClientCache cache;
  private static Region<Object, Object> region;

  private static GenericContainer<?> createGeodeServer() {
    if (testLatestDeps()) {
      return new GenericContainer<>(
          new ImageFromDockerfile().withFileFromClasspath("Dockerfile", "geode-2.0.2.Dockerfile"));
    }
    return new GenericContainer<>("apachegeode/geode:1.4.0");
  }

  @BeforeAll
  static void setUp() {
    cleanup.deferAfterAll(geodeServer::stop);
    geodeServer.start();

    cache =
        new ClientCacheFactory()
            .addPoolServer(geodeServer.getHost(), geodeServer.getMappedPort(GEODE_PORT))
            .create();
    cleanup.deferAfterAll(cache);

    ClientRegionFactory<Object, Object> regionFactory =
        cache.createClientRegionFactory(ClientRegionShortcut.PROXY);
    region = regionFactory.create("test-region");
  }

  private static Stream<Arguments> provideParameters() {
    return Stream.of(
        Arguments.of("Hello", "World"),
        Arguments.of("Humpty", "Dumpty"),
        Arguments.of(Integer.valueOf(1), "One"),
        Arguments.of("One", Integer.valueOf(1)));
  }

  @Test
  void testDurationMetric() {
    region.put("key", "value");

    assertDurationMetric(
        testing,
        "io.opentelemetry.geode-1.4",
        DB_SYSTEM_NAME,
        DB_COLLECTION_NAME,
        DB_OPERATION_NAME,
        SERVER_ADDRESS,
        SERVER_PORT);
  }

  @ParameterizedTest
  @MethodSource("provideParameters")
  void testPutAndGet(Object key, Object value) {
    Object cacheValue =
        testing.runWithSpan(
            "someTrace",
            () -> {
              region.clear();
              region.put(key, value);
              return region.get(key);
            });
    assertThat(cacheValue).isEqualTo(value);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("someTrace").hasKind(SpanKind.INTERNAL),
                span ->
                    span.hasName("clear test-region")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), GEODE),
                            equalTo(
                                DB_COLLECTION_NAME,
                                emitStableDatabaseSemconv() ? "test-region" : null),
                            equalTo(DB_NAME, emitStableDatabaseSemconv() ? null : "test-region"),
                            equalTo(maybeStable(DB_OPERATION), "clear"),
                            equalTo(SERVER_ADDRESS, geodeServer.getHost()),
                            equalTo(SERVER_PORT, geodeServer.getMappedPort(GEODE_PORT))),
                span ->
                    span.hasName("put test-region")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), GEODE),
                            equalTo(
                                DB_COLLECTION_NAME,
                                emitStableDatabaseSemconv() ? "test-region" : null),
                            equalTo(DB_NAME, emitStableDatabaseSemconv() ? null : "test-region"),
                            equalTo(maybeStable(DB_OPERATION), "put"),
                            equalTo(SERVER_ADDRESS, geodeServer.getHost()),
                            equalTo(SERVER_PORT, geodeServer.getMappedPort(GEODE_PORT))),
                span ->
                    span.hasName("get test-region")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), GEODE),
                            equalTo(
                                DB_COLLECTION_NAME,
                                emitStableDatabaseSemconv() ? "test-region" : null),
                            equalTo(DB_NAME, emitStableDatabaseSemconv() ? null : "test-region"),
                            equalTo(maybeStable(DB_OPERATION), "get"),
                            equalTo(SERVER_ADDRESS, geodeServer.getHost()),
                            equalTo(SERVER_PORT, geodeServer.getMappedPort(GEODE_PORT)))));
  }

  @ParameterizedTest
  @MethodSource("provideParameters")
  void testPutAndRemove(Object key, Object value) {
    testing.runWithSpan(
        "someTrace",
        () -> {
          region.clear();
          region.put(key, value);
          region.remove(key);
        });
    assertThat(region.isEmptyOnServer()).isTrue();
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("someTrace").hasKind(SpanKind.INTERNAL),
                span ->
                    span.hasName("clear test-region")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), GEODE),
                            equalTo(
                                DB_COLLECTION_NAME,
                                emitStableDatabaseSemconv() ? "test-region" : null),
                            equalTo(DB_NAME, emitStableDatabaseSemconv() ? null : "test-region"),
                            equalTo(maybeStable(DB_OPERATION), "clear"),
                            equalTo(SERVER_ADDRESS, geodeServer.getHost()),
                            equalTo(SERVER_PORT, geodeServer.getMappedPort(GEODE_PORT))),
                span ->
                    span.hasName("put test-region")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), GEODE),
                            equalTo(
                                DB_COLLECTION_NAME,
                                emitStableDatabaseSemconv() ? "test-region" : null),
                            equalTo(DB_NAME, emitStableDatabaseSemconv() ? null : "test-region"),
                            equalTo(maybeStable(DB_OPERATION), "put"),
                            equalTo(SERVER_ADDRESS, geodeServer.getHost()),
                            equalTo(SERVER_PORT, geodeServer.getMappedPort(GEODE_PORT))),
                span ->
                    span.hasName("remove test-region")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), GEODE),
                            equalTo(
                                DB_COLLECTION_NAME,
                                emitStableDatabaseSemconv() ? "test-region" : null),
                            equalTo(DB_NAME, emitStableDatabaseSemconv() ? null : "test-region"),
                            equalTo(maybeStable(DB_OPERATION), "remove"),
                            equalTo(SERVER_ADDRESS, geodeServer.getHost()),
                            equalTo(SERVER_PORT, geodeServer.getMappedPort(GEODE_PORT)))));
  }

  @ParameterizedTest
  @MethodSource("provideParameters")
  void testQuery(Object key, Object value) throws QueryException {
    SelectResults<Object> cacheValue =
        testing.runWithSpan(
            "someTrace",
            () -> {
              region.clear();
              region.put(key, value);
              return region.query("SELECT * FROM /test-region");
            });
    assertThat(cacheValue).hasSize(1);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("someTrace").hasKind(SpanKind.INTERNAL),
                span ->
                    span.hasName("clear test-region")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), GEODE),
                            equalTo(
                                DB_COLLECTION_NAME,
                                emitStableDatabaseSemconv() ? "test-region" : null),
                            equalTo(DB_NAME, emitStableDatabaseSemconv() ? null : "test-region"),
                            equalTo(maybeStable(DB_OPERATION), "clear"),
                            equalTo(SERVER_ADDRESS, geodeServer.getHost()),
                            equalTo(SERVER_PORT, geodeServer.getMappedPort(GEODE_PORT))),
                span ->
                    span.hasName("put test-region")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), GEODE),
                            equalTo(
                                DB_COLLECTION_NAME,
                                emitStableDatabaseSemconv() ? "test-region" : null),
                            equalTo(DB_NAME, emitStableDatabaseSemconv() ? null : "test-region"),
                            equalTo(maybeStable(DB_OPERATION), "put"),
                            equalTo(SERVER_ADDRESS, geodeServer.getHost()),
                            equalTo(SERVER_PORT, geodeServer.getMappedPort(GEODE_PORT))),
                span ->
                    span.hasName("query test-region")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), GEODE),
                            equalTo(
                                DB_COLLECTION_NAME,
                                emitStableDatabaseSemconv() ? "test-region" : null),
                            equalTo(DB_NAME, emitStableDatabaseSemconv() ? null : "test-region"),
                            equalTo(maybeStable(DB_OPERATION), "query"),
                            equalTo(maybeStable(DB_STATEMENT), "SELECT * FROM /test-region"),
                            equalTo(SERVER_ADDRESS, geodeServer.getHost()),
                            equalTo(SERVER_PORT, geodeServer.getMappedPort(GEODE_PORT)))));
  }

  @ParameterizedTest
  @MethodSource("provideParameters")
  void testExistsValue(Object key, Object value) throws QueryException {
    boolean cacheValue =
        testing.runWithSpan(
            "someTrace",
            () -> {
              region.clear();
              region.put(key, value);
              return region.existsValue("SELECT * FROM /test-region");
            });
    assertThat(cacheValue).isTrue();
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("someTrace").hasKind(SpanKind.INTERNAL),
                span ->
                    span.hasName("clear test-region")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), GEODE),
                            equalTo(
                                DB_COLLECTION_NAME,
                                emitStableDatabaseSemconv() ? "test-region" : null),
                            equalTo(DB_NAME, emitStableDatabaseSemconv() ? null : "test-region"),
                            equalTo(maybeStable(DB_OPERATION), "clear"),
                            equalTo(SERVER_ADDRESS, geodeServer.getHost()),
                            equalTo(SERVER_PORT, geodeServer.getMappedPort(GEODE_PORT))),
                span ->
                    span.hasName("put test-region")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), GEODE),
                            equalTo(
                                DB_COLLECTION_NAME,
                                emitStableDatabaseSemconv() ? "test-region" : null),
                            equalTo(DB_NAME, emitStableDatabaseSemconv() ? null : "test-region"),
                            equalTo(maybeStable(DB_OPERATION), "put"),
                            equalTo(SERVER_ADDRESS, geodeServer.getHost()),
                            equalTo(SERVER_PORT, geodeServer.getMappedPort(GEODE_PORT))),
                span ->
                    span.hasName("existsValue test-region")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), GEODE),
                            equalTo(
                                DB_COLLECTION_NAME,
                                emitStableDatabaseSemconv() ? "test-region" : null),
                            equalTo(DB_NAME, emitStableDatabaseSemconv() ? null : "test-region"),
                            equalTo(maybeStable(DB_OPERATION), "existsValue"),
                            equalTo(maybeStable(DB_STATEMENT), "SELECT * FROM /test-region"),
                            equalTo(SERVER_ADDRESS, geodeServer.getHost()),
                            equalTo(SERVER_PORT, geodeServer.getMappedPort(GEODE_PORT)))));
  }

  @Test
  void shouldSanitizeGeodeQuery() throws QueryException {
    Card value = new Card("1234432156788765", "10/2020");
    SelectResults<Object> results =
        testing.runWithSpan(
            "someTrace",
            () -> {
              region.clear();
              region.put(1, value);
              return region.query("SELECT * FROM /test-region p WHERE p.expDate = '10/2020'");
            });

    assertThat(results.asList()).singleElement().usingRecursiveComparison().isEqualTo(value);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("someTrace").hasKind(SpanKind.INTERNAL),
                span ->
                    span.hasName("clear test-region")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), GEODE),
                            equalTo(
                                DB_COLLECTION_NAME,
                                emitStableDatabaseSemconv() ? "test-region" : null),
                            equalTo(DB_NAME, emitStableDatabaseSemconv() ? null : "test-region"),
                            equalTo(maybeStable(DB_OPERATION), "clear"),
                            equalTo(SERVER_ADDRESS, geodeServer.getHost()),
                            equalTo(SERVER_PORT, geodeServer.getMappedPort(GEODE_PORT))),
                span ->
                    span.hasName("put test-region")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), GEODE),
                            equalTo(
                                DB_COLLECTION_NAME,
                                emitStableDatabaseSemconv() ? "test-region" : null),
                            equalTo(DB_NAME, emitStableDatabaseSemconv() ? null : "test-region"),
                            equalTo(maybeStable(DB_OPERATION), "put"),
                            equalTo(SERVER_ADDRESS, geodeServer.getHost()),
                            equalTo(SERVER_PORT, geodeServer.getMappedPort(GEODE_PORT))),
                span ->
                    span.hasName("query test-region")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), GEODE),
                            equalTo(
                                DB_COLLECTION_NAME,
                                emitStableDatabaseSemconv() ? "test-region" : null),
                            equalTo(DB_NAME, emitStableDatabaseSemconv() ? null : "test-region"),
                            equalTo(maybeStable(DB_OPERATION), "query"),
                            equalTo(
                                maybeStable(DB_STATEMENT),
                                "SELECT * FROM /test-region p WHERE p.expDate = ?"),
                            equalTo(SERVER_ADDRESS, geodeServer.getHost()),
                            equalTo(SERVER_PORT, geodeServer.getMappedPort(GEODE_PORT)))));
  }

  public static class Card implements PdxSerializable {
    private String cardNumber;
    private String expDate;

    public Card() {}

    public Card(String cardNumber, String expDate) {
      this.cardNumber = cardNumber;
      this.expDate = expDate;
    }

    public String getCardNumber() {
      return cardNumber;
    }

    public void setCardNumber(String cardNumber) {
      this.cardNumber = cardNumber;
    }

    public String getExpDate() {
      return expDate;
    }

    public void setExpDate(String expDate) {
      this.expDate = expDate;
    }

    @Override
    public void toData(PdxWriter writer) {
      writer.writeString("cardNumber", cardNumber);
      writer.writeString("expDate", expDate);
    }

    @Override
    public void fromData(PdxReader reader) {
      cardNumber = reader.readString("cardNumber");
      expDate = reader.readString("expDate");
    }
  }
}
