/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_0;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.internal.AgentDistributionConfig;
import io.opentelemetry.javaagent.instrumentation.couchbase.v2_0.network.CouchbaseNetworkInstrumentationModule;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CouchbaseInstrumentationModulesTest {
  private final InstrumentationModule core = new CouchbaseInstrumentationModule();
  private final InstrumentationModule network = new CouchbaseNetworkInstrumentationModule();
  private final InstrumentationModule network26 =
      new io.opentelemetry.javaagent.instrumentation.couchbase.v2_6
          .CouchbaseInstrumentationModule();

  @Test
  void preservesOrderedEnablementNames() {
    assertThat(core.instrumentationNames())
        .containsExactly("couchbase", "couchbase-2.0", "couchbase-2.0-core");
    assertThat(network.instrumentationNames())
        .containsExactly(
            "couchbase", "couchbase-2.0", "couchbase-network-2.0", "couchbase-2.0-network");
    assertThat(network26.instrumentationNames())
        .containsExactly("couchbase", "couchbase-2.6", "couchbase-2.6-network");
  }

  @ParameterizedTest
  @CsvSource({
    "couchbase, false, false, false",
    "couchbase-2.0, false, false, true",
    "couchbase-2.0-core, false, true, true",
    "couchbase-network-2.0, true, false, true",
    "couchbase-2.0-network, true, false, true",
    "couchbase-2.6, true, true, false",
    "couchbase-2.6-network, true, true, false"
  })
  void disablesOnlySelectedModules(
      String name, boolean coreEnabled, boolean networkEnabled, boolean network26Enabled) {
    ConfigProperties properties = mock(ConfigProperties.class);
    when(properties.getBoolean(anyString())).thenReturn(null);
    when(properties.getBoolean("otel.instrumentation." + name + ".enabled")).thenReturn(false);
    AgentDistributionConfig config = AgentDistributionConfig.fromConfigProperties(properties);

    assertThat(config.isInstrumentationEnabled(core.instrumentationNames(), true))
        .isEqualTo(coreEnabled);
    assertThat(config.isInstrumentationEnabled(network.instrumentationNames(), true))
        .isEqualTo(networkEnabled);
    assertThat(config.isInstrumentationEnabled(network26.instrumentationNames(), true))
        .isEqualTo(network26Enabled);
  }

  @Test
  void existingAliasTakesPrecedenceOverNewNetworkAlias() {
    ConfigProperties properties = mock(ConfigProperties.class);
    when(properties.getBoolean(anyString())).thenReturn(null);
    when(properties.getBoolean("otel.instrumentation.couchbase-2.6.enabled")).thenReturn(true);
    when(properties.getBoolean("otel.instrumentation.couchbase-2.6-network.enabled"))
        .thenReturn(false);
    AgentDistributionConfig config = AgentDistributionConfig.fromConfigProperties(properties);

    assertThat(config.isInstrumentationEnabled(network26.instrumentationNames(), false)).isTrue();
  }
}
