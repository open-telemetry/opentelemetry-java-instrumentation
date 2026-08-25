/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.geode.v1_4;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.instrumentation.testing.junit.db.DbClientMetricsTestUtil.assertDurationMetric;
import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.DbAttributes.DB_COLLECTION_NAME;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.GEODE;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.function.Consumer;
import org.apache.geode.cache.Region;
import org.apache.geode.cache.client.ClientCache;
import org.apache.geode.cache.client.ClientCacheFactory;
import org.apache.geode.cache.client.ClientRegionFactory;
import org.apache.geode.cache.client.ClientRegionShortcut;
import org.apache.geode.cache.client.PoolFactory;
import org.apache.geode.cache.client.PoolManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * The pools here name servers nobody is listening on. Only {@code putAll} with an empty map is run
 * against them, which Geode completes without reaching a server, so every span describes the target
 * its pool was configured with rather than a failure to connect.
 */
@SuppressWarnings("deprecation") // using deprecated semconv
class ConfiguredTargetTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final ClientCache cache =
      new ClientCacheFactory().setPoolMinConnections(0).setPoolSubscriptionEnabled(false).create();

  @AfterAll
  static void closeCache() {
    cache.close();
  }

  @Test
  void singleConfiguredServerIsReported() {
    Region<Object, Object> region =
        createRegion("single-server", poolFactory -> poolFactory.addServer("localhost", 40404));

    region.putAll(emptyMap());

    testing.waitAndAssertTraces(operation(region, "localhost", 40404L));
    assertDurationMetric(
        testing,
        "io.opentelemetry.geode-1.4",
        DB_SYSTEM_NAME,
        DB_COLLECTION_NAME,
        DB_OPERATION_NAME,
        SERVER_ADDRESS,
        SERVER_PORT);
  }

  @Test
  void severalConfiguredServersAreNotReported() {
    Region<Object, Object> region =
        createRegion(
            "several-servers",
            poolFactory -> {
              poolFactory.addServer("127.0.0.1", 40404);
              poolFactory.addServer("127.0.0.2", 40405);
            });

    region.putAll(emptyMap());

    testing.waitAndAssertTraces(operation(region, null, null));
    assertDurationMetric(
        testing,
        "io.opentelemetry.geode-1.4",
        DB_SYSTEM_NAME,
        DB_COLLECTION_NAME,
        DB_OPERATION_NAME);
  }

  @Test
  void rejectedServerDoesNotReplaceConfiguredLocator() {
    PoolFactory poolFactory = poolFactory();
    poolFactory.addLocator("127.0.0.2", 10334);
    assertThatThrownBy(() -> poolFactory.addServer("localhost", 40404))
        .isInstanceOf(IllegalStateException.class);
    Region<Object, Object> region = createRegion("server-and-locator", poolFactory);

    region.putAll(emptyMap());

    testing.waitAndAssertTraces(operation(region, "127.0.0.2", null));
  }

  @Test
  void configuredLocatorIsReportedWithoutItsPort() {
    Region<Object, Object> region =
        createRegion("locators", poolFactory -> poolFactory.addLocator("localhost", 10334));

    region.putAll(emptyMap());

    testing.waitAndAssertTraces(operation(region, "localhost", null));
    assertDurationMetric(
        testing,
        "io.opentelemetry.geode-1.4",
        DB_SYSTEM_NAME,
        DB_COLLECTION_NAME,
        DB_OPERATION_NAME,
        SERVER_ADDRESS);
  }

  @Test
  void severalConfiguredLocatorsAreNotReported() {
    Region<Object, Object> region =
        createRegion(
            "several-locators",
            poolFactory -> {
              poolFactory.addLocator("127.0.0.2", 10335);
              poolFactory.addLocator("127.0.0.1", 10334);
            });

    region.putAll(emptyMap());

    testing.waitAndAssertTraces(operation(region, null, null));
  }

  @Test
  void ipv6ServersKeepTheirAddress() {
    Region<Object, Object> bare =
        createRegion("ipv6-bare", poolFactory -> poolFactory.addServer("::1", 40404));
    Region<Object, Object> bracketed =
        createRegion(
            "ipv6-bracketed", poolFactory -> poolFactory.addServer("[2001:db8::1]", 40404));

    bare.putAll(emptyMap());
    bracketed.putAll(emptyMap());

    testing.waitAndAssertTraces(
        operation(bare, "::1", 40404L), operation(bracketed, "2001:db8::1", 40404L));
  }

  @Test
  void poolKeepsTheTargetItWasCreatedWith() {
    PoolFactory poolFactory = poolFactory();
    poolFactory.addServer("127.0.0.1", 40404);
    Region<Object, Object> first = createRegion("first-pool", poolFactory);

    poolFactory.addServer("127.0.0.2", 40405);
    Region<Object, Object> second = createRegion("second-pool", poolFactory);

    first.putAll(emptyMap());
    second.putAll(emptyMap());

    testing.waitAndAssertTraces(
        operation(first, "127.0.0.1", 40404L), operation(second, null, null));
  }

  @Test
  void resetForgetsWhatWasConfiguredBefore() {
    Region<Object, Object> region =
        createRegion(
            "after-reset",
            poolFactory -> {
              poolFactory.addServer("127.0.0.1", 40404);
              poolFactory.reset();
              poolFactory
                  .setMinConnections(0)
                  .setSubscriptionEnabled(false)
                  .addServer("127.0.0.2", 40405);
            });

    region.putAll(emptyMap());

    testing.waitAndAssertTraces(operation(region, "127.0.0.2", 40405L));
  }

  private static Region<Object, Object> createRegion(String name, Consumer<PoolFactory> configure) {
    PoolFactory poolFactory = poolFactory();
    configure.accept(poolFactory);
    return createRegion(name, poolFactory);
  }

  private static Region<Object, Object> createRegion(String name, PoolFactory poolFactory) {
    poolFactory.create(name + "-pool");
    ClientRegionFactory<Object, Object> regionFactory =
        cache.createClientRegionFactory(ClientRegionShortcut.PROXY);
    regionFactory.setPoolName(name + "-pool");
    return regionFactory.create(name);
  }

  private static PoolFactory poolFactory() {
    return PoolManager.createFactory().setMinConnections(0).setSubscriptionEnabled(false);
  }

  private static Consumer<TraceAssert> operation(
      Region<Object, Object> region, String serverAddress, Long serverPort) {
    String regionName = region.getName();
    List<InetSocketAddress> legacyServers = PoolManager.find(region).getServers();
    String legacyServerAddress =
        legacyServers.size() == 1 ? legacyServers.get(0).getHostString() : null;
    Long legacyServerPort =
        legacyServers.size() == 1 ? (long) legacyServers.get(0).getPort() : null;
    return trace ->
        trace.hasSpansSatisfyingExactly(
            span ->
                span.hasName("putAll " + regionName)
                    .hasKind(SpanKind.CLIENT)
                    .hasAttributesSatisfyingExactly(
                        equalTo(maybeStable(DB_SYSTEM), GEODE),
                        equalTo(
                            DB_COLLECTION_NAME, emitStableDatabaseSemconv() ? regionName : null),
                        equalTo(DB_NAME, emitStableDatabaseSemconv() ? null : regionName),
                        equalTo(maybeStable(DB_OPERATION), "putAll"),
                        equalTo(
                            SERVER_ADDRESS,
                            emitStableDatabaseSemconv() ? serverAddress : legacyServerAddress),
                        equalTo(
                            SERVER_PORT,
                            emitStableDatabaseSemconv() ? serverPort : legacyServerPort)));
  }
}
