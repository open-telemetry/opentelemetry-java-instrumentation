/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.geode.v1_4;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.DbAttributes.DB_COLLECTION_NAME;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.GEODE;
import static java.util.Collections.emptyMap;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.apache.geode.cache.Region;
import org.apache.geode.cache.client.ClientCache;
import org.apache.geode.cache.client.ClientCacheFactory;
import org.apache.geode.cache.client.ClientRegionShortcut;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * A cache that configures its own pool, which every region that names no pool reaches its servers
 * through. The cache here is the only one that holds a pool, so the region below can only reach the
 * pool the cache configured.
 *
 * <p>The pool names a server nobody is listening on. Only {@code putAll} with an empty map is run
 * against it, which Geode completes without reaching a server, so the span describes the target the
 * pool was configured with rather than a failure to connect.
 */
@SuppressWarnings("deprecation") // using deprecated semconv
class DefaultPoolTargetTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final ClientCache cache =
      new ClientCacheFactory()
          .setPoolMinConnections(0)
          .setPoolSubscriptionEnabled(false)
          .addPoolServer("localhost", 40404)
          .create();

  @AfterAll
  static void closeCache() {
    cache.close();
  }

  @Test
  void theServerTheCacheConfiguredItsPoolWithIsReported() {
    Region<Object, Object> region =
        cache
            .<Object, Object>createClientRegionFactory(ClientRegionShortcut.PROXY)
            .create("default-pool-region");

    region.putAll(emptyMap());

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("putAll default-pool-region")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), GEODE),
                            equalTo(
                                DB_COLLECTION_NAME,
                                emitStableDatabaseSemconv() ? "default-pool-region" : null),
                            equalTo(
                                DB_NAME,
                                emitStableDatabaseSemconv() ? null : "default-pool-region"),
                            equalTo(maybeStable(DB_OPERATION), "putAll"),
                            equalTo(SERVER_ADDRESS, "localhost"),
                            equalTo(SERVER_PORT, 40404L))));
  }
}
