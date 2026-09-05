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
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyMap;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.geode.cache.Region;
import org.apache.geode.cache.client.ClientCache;
import org.apache.geode.cache.client.ClientCacheFactory;
import org.apache.geode.cache.client.ClientRegionShortcut;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

class CacheXmlTargetTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  @SuppressWarnings("deprecation") // using deprecated semconv
  void configuredLocatorGroupSurvivesCacheXmlPoolCopy(@TempDir Path tempDir) throws IOException {
    Path cacheXml = tempDir.resolve("client-cache.xml");
    Files.write(
        cacheXml,
        ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<client-cache xmlns=\"http://geode.apache.org/schema/cache\"\n"
                + "    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
                + "    xsi:schemaLocation=\"http://geode.apache.org/schema/cache "
                + "http://geode.apache.org/schema/cache/cache-1.0.xsd\"\n"
                + "    version=\"1.0\">\n"
                + "  <pool name=\"xml-pool\" server-group=\"orders\" min-connections=\"0\" "
                + "subscription-enabled=\"false\">\n"
                + "    <locator host=\"192.0.2.1\" port=\"10334\"/>\n"
                + "    <locator host=\"192.0.2.2\" port=\"10335\"/>\n"
                + "  </pool>\n"
                + "</client-cache>\n")
            .getBytes(UTF_8));

    try (ClientCache cache =
        new ClientCacheFactory().set("cache-xml-file", cacheXml.toString()).create()) {
      Region<Object, Object> region =
          cache
              .<Object, Object>createClientRegionFactory(ClientRegionShortcut.PROXY)
              .setPoolName("xml-pool")
              .create("xml-region");

      region.putAll(emptyMap());

      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasName("putAll xml-region")
                          .hasKind(SpanKind.CLIENT)
                          .hasAttributesSatisfyingExactly(
                              equalTo(maybeStable(DB_SYSTEM), GEODE),
                              equalTo(
                                  DB_COLLECTION_NAME,
                                  emitStableDatabaseSemconv() ? "xml-region" : null),
                              equalTo(DB_NAME, emitStableDatabaseSemconv() ? null : "xml-region"),
                              equalTo(maybeStable(DB_OPERATION), "putAll"),
                              equalTo(
                                  SERVER_ADDRESS,
                                  emitStableDatabaseSemconv()
                                      ? "192.0.2.1:10334,192.0.2.2:10335/orders"
                                      : null),
                              equalTo(SERVER_PORT, null))));
    }
  }
}
