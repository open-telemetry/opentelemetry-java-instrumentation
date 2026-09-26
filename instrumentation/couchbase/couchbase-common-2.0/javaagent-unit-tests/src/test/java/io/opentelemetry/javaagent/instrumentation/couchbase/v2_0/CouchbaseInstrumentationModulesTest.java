/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_0;

import static java.util.logging.Level.WARNING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.opentelemetry.instrumentation.api.incubator.config.internal.CommonConfig;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.internal.AgentDistributionConfig;
import io.opentelemetry.javaagent.extension.instrumentation.internal.DeprecatedInstrumentationNames;
import io.opentelemetry.javaagent.instrumentation.couchbase.v2_0.network.CouchbaseNetworkInstrumentationModule;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

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
        .containsExactly("couchbase", "couchbase-2.0", "couchbase-2.6", "couchbase-2.6-network");
  }

  @ParameterizedTest
  @CsvSource({
    "couchbase, false, false, false",
    "couchbase-2.0, false, false, false",
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
  void deprecatedAliasTakesPrecedenceOverNetworkSelector() {
    ConfigProperties properties = mock(ConfigProperties.class);
    when(properties.getBoolean(anyString())).thenReturn(null);
    when(properties.getBoolean("otel.instrumentation.couchbase-2.6.enabled")).thenReturn(true);
    when(properties.getBoolean("otel.instrumentation.couchbase-2.6-network.enabled"))
        .thenReturn(false);
    AgentDistributionConfig config = AgentDistributionConfig.fromConfigProperties(properties);

    assertThat(config.isInstrumentationEnabled(network26.instrumentationNames(), false)).isTrue();
  }

  @Test
  void ownerAliasTakesPrecedenceOverDeprecatedAlias() {
    ConfigProperties properties = mock(ConfigProperties.class);
    when(properties.getBoolean(anyString())).thenReturn(null);
    when(properties.getBoolean("otel.instrumentation.couchbase-2.0.enabled")).thenReturn(false);
    when(properties.getBoolean("otel.instrumentation.couchbase-2.6.enabled")).thenReturn(true);
    AgentDistributionConfig config = AgentDistributionConfig.fromConfigProperties(properties);

    assertThat(config.isInstrumentationEnabled(network26.instrumentationNames(), true)).isFalse();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void deprecatedAliasWarnsOutsidePreviewAndIsIgnoredInPreview(boolean v3Preview) {
    ConfigProperties properties = mock(ConfigProperties.class);
    when(properties.getBoolean(anyString())).thenReturn(null);
    when(properties.getBoolean("otel.instrumentation.couchbase-2.6.enabled")).thenReturn(true);
    AgentDistributionConfig config = AgentDistributionConfig.fromConfigProperties(properties);
    CommonConfig commonConfig = mock(CommonConfig.class);
    when(commonConfig.isV3Preview()).thenReturn(v3Preview);
    Logger logger = Logger.getLogger(DeprecatedInstrumentationNames.class.getName());
    List<LogRecord> records = new ArrayList<>();
    Handler handler =
        new Handler() {
          @Override
          public void publish(LogRecord record) {
            records.add(record);
          }

          @Override
          public void flush() {}

          @Override
          public void close() {}
        };
    logger.addHandler(handler);
    try (MockedStatic<AgentCommonConfig> common = mockStatic(AgentCommonConfig.class);
        MockedStatic<AgentDistributionConfig> distribution =
            mockStatic(AgentDistributionConfig.class)) {
      common.when(AgentCommonConfig::get).thenReturn(commonConfig);
      distribution.when(AgentDistributionConfig::get).thenReturn(config);

      InstrumentationModule module =
          new io.opentelemetry.javaagent.instrumentation.couchbase.v2_6
              .CouchbaseInstrumentationModule();
      if (v3Preview) {
        assertThat(module.instrumentationNames())
            .containsExactly("couchbase", "couchbase-2.0", "couchbase-2.6-network");
        assertThat(config.isInstrumentationEnabled(module.instrumentationNames(), false)).isFalse();
        assertThat(records).isEmpty();
      } else {
        assertThat(module.instrumentationNames())
            .containsExactly(
                "couchbase", "couchbase-2.0", "couchbase-2.6", "couchbase-2.6-network");
        assertThat(config.isInstrumentationEnabled(module.instrumentationNames(), false)).isTrue();
        assertThat(records)
            .singleElement()
            .satisfies(
                record -> {
                  assertThat(record.getLevel()).isEqualTo(WARNING);
                  assertThat(record.getMessage())
                      .isEqualTo(
                          "otel.instrumentation.{0}.enabled is deprecated; "
                              + "use otel.instrumentation.{1}.enabled instead.");
                  assertThat(record.getParameters())
                      .containsExactly("couchbase-2.6", "couchbase-2.0");
                });
      }
    } finally {
      logger.removeHandler(handler);
    }
  }
}
