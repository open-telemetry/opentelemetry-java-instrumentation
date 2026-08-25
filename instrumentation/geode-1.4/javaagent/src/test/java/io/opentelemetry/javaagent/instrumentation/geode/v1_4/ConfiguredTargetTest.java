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

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
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
  void severalConfiguredServersAreReportedTogether() {
    Region<Object, Object> region =
        createRegion(
            "several-servers",
            poolFactory -> {
              poolFactory.addServer("127.0.0.1", 40404);
              poolFactory.addServer("127.0.0.2", 40405);
            });

    region.putAll(emptyMap());

    testing.waitAndAssertTraces(operation(region, "127.0.0.1:40404,127.0.0.2:40405", null));
    assertDurationMetric(
        testing,
        "io.opentelemetry.geode-1.4",
        DB_SYSTEM_NAME,
        DB_COLLECTION_NAME,
        DB_OPERATION_NAME,
        SERVER_ADDRESS);
  }

  @Test
  void explicitlyConfiguredServerIsPreferredOverItsGroup() {
    Region<Object, Object> region =
        createRegion(
            "server-group",
            poolFactory -> {
              poolFactory.addServer("localhost", 40404);
              poolFactory.setServerGroup("orders");
            });

    region.putAll(emptyMap());

    testing.waitAndAssertTraces(operation(region, "localhost", 40404L));
  }

  @Test
  void configuredLocatorIsReportedAsADiscoveryTarget() {
    Region<Object, Object> region =
        createRegion("locators", poolFactory -> poolFactory.addLocator("localhost", 10334));

    region.putAll(emptyMap());

    testing.waitAndAssertTraces(operation(region, "localhost:10334", null));
    assertDurationMetric(
        testing,
        "io.opentelemetry.geode-1.4",
        DB_SYSTEM_NAME,
        DB_COLLECTION_NAME,
        DB_OPERATION_NAME,
        SERVER_ADDRESS);
  }

  @Test
  void configuredLocatorsAreIndependentlyScopedByTheirGroup() {
    Region<Object, Object> region =
        createRegion(
            "locator-group",
            poolFactory -> {
              poolFactory.addLocator("127.0.0.2", 10335);
              poolFactory.addLocator("127.0.0.1", 10334);
              poolFactory.setServerGroup("orders");
            });

    region.putAll(emptyMap());

    testing.waitAndAssertTraces(
        operation(region, "127.0.0.1:10334/orders,127.0.0.2:10335/orders", null));
  }

  @Test
  void ipv6ServersKeepTheirAddress() {
    Region<Object, Object> single =
        createRegion("ipv6-single", poolFactory -> poolFactory.addServer("::1", 40404));
    Region<Object, Object> several =
        createRegion(
            "ipv6-several",
            poolFactory -> {
              poolFactory.addServer("[2001:db8::1]", 40404);
              poolFactory.addServer("127.0.0.2", 40405);
            });

    single.putAll(emptyMap());
    several.putAll(emptyMap());

    testing.waitAndAssertTraces(
        operation(single, "::1", 40404L),
        operation(several, "127.0.0.2:40405,[2001:db8::1]:40404", null));
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
        operation(first, "127.0.0.1", 40404L),
        operation(second, "127.0.0.1:40404,127.0.0.2:40405", null));
  }

  @Test
  void resetForgetsWhatWasConfiguredBefore() {
    Region<Object, Object> region =
        createRegion(
            "after-reset",
            poolFactory -> {
              poolFactory.addServer("127.0.0.1", 40404);
              poolFactory.setServerGroup("orders");
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
                        equalTo(SERVER_ADDRESS, emitStableDatabaseSemconv() ? serverAddress : null),
                        equalTo(SERVER_PORT, emitStableDatabaseSemconv() ? serverPort : null)));
  }
}
