/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.instrumentation.testing.junit.service.SemconvServiceStabilityUtil.maybeStablePeerService;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.semconv.DbAttributes.DbSystemNameValues.POSTGRESQL;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.semconv.service.peer.ServicePeerAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.jdbc.internal.dbinfo.DbInfo;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Verifies service peer mappings against the value emitted as {@code server.address}. */
class JdbcServicePeerTest {

  private static final String GROUP_TARGET = "postgresql://pg.host1:5432,pg.host2:5433";

  @Test
  void groupTargetCanBeMatchedExactly() {
    DbRequest request =
        request(
            DbInfo.builder()
                .dbSystemName(POSTGRESQL)
                .serverAddress("localhost")
                .serverPort(5432)
                .serverAddressGroup(GROUP_TARGET)
                .build());

    if (emitStableDatabaseSemconv()) {
      assertThat(resolve(request, "localhost")).isEmpty();
      assertThat(resolve(request, GROUP_TARGET))
          .containsOnly(entry(maybeStablePeerService(), "myService"));
    } else {
      assertThat(resolve(request, "localhost"))
          .containsOnly(entry(maybeStablePeerService(), "myService"));
      assertThat(resolve(request, GROUP_TARGET)).isEmpty();
    }
  }

  @Test
  void singleHostIsMatchedByAHostKeyedMappingInEveryMode() {
    DbRequest request =
        request(
            DbInfo.builder()
                .dbSystemName(POSTGRESQL)
                .serverAddress("localhost")
                .serverPort(5432)
                .build());

    assertThat(resolve(request, "localhost"))
        .containsOnly(entry(maybeStablePeerService(), "myService"));
  }

  private static DbRequest request(DbInfo dbInfo) {
    return DbRequest.create(dbInfo, "SELECT 1", false);
  }

  private static Attributes resolve(DbRequest request, String peer) {
    AttributesExtractor<DbRequest, Void> extractor =
        ServicePeerAttributesExtractor.create(new JdbcAttributesGetter(), openTelemetry(peer));
    AttributesBuilder attributes = Attributes.builder();
    extractor.onEnd(attributes, Context.root(), request, null, null);
    return attributes.build();
  }

  private static ExtendedOpenTelemetry openTelemetry(String peer) {
    DeclarativeConfigProperties entry = mock(DeclarativeConfigProperties.class);
    when(entry.getString("peer")).thenReturn(peer);
    when(entry.getString("service_name")).thenReturn("myService");
    when(entry.getString("service_namespace")).thenReturn(null);

    List<DeclarativeConfigProperties> entries = asList(entry);
    DeclarativeConfigProperties commonConfig = mock(DeclarativeConfigProperties.class);
    when(commonConfig.getStructuredList("service_peer_mapping", emptyList())).thenReturn(entries);

    ExtendedOpenTelemetry openTelemetry = mock(ExtendedOpenTelemetry.class);
    when(openTelemetry.getInstrumentationConfig("common")).thenReturn(commonConfig);
    return openTelemetry;
  }
}
