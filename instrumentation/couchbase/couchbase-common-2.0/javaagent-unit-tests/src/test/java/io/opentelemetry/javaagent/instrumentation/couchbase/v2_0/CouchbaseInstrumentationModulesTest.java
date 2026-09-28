/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_0;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.opentelemetry.instrumentation.api.incubator.config.internal.CommonConfig;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.internal.AgentDistributionConfig;
import io.opentelemetry.javaagent.extension.instrumentation.internal.DeprecatedInstrumentationNames;
import io.opentelemetry.javaagent.instrumentation.couchbase.v2_0.network.v2_0.CouchbaseNetworkInstrumentationModule;
import io.opentelemetry.javaagent.instrumentation.couchbase.v2_0.network.v2_6.CouchbaseNetwork26InstrumentationModule;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

class CouchbaseInstrumentationModulesTest {

  @Test
  void registersBothNetworkAdvices() {
    assertThat(modules(false)[1].typeInstrumentations())
        .extracting(instrumentation -> instrumentation.getClass().getName())
        .containsExactly(
            "io.opentelemetry.javaagent.instrumentation.couchbase.v2_0.network.v2_0.CouchbaseCoreNetworkInstrumentation",
            "io.opentelemetry.javaagent.instrumentation.couchbase.v2_0.network.v2_0.CouchbaseNetworkInstrumentation");
    assertThat(modules(false)[2].typeInstrumentations())
        .extracting(instrumentation -> instrumentation.getClass().getName())
        .containsExactly(
            "io.opentelemetry.javaagent.instrumentation.couchbase.v2_0.network.v2_6.CouchbaseCoreInstrumentation",
            "io.opentelemetry.javaagent.instrumentation.couchbase.v2_0.network.v2_6.CouchbaseNetworkInstrumentation");
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void preservesOrderedEnablementNames(boolean v3Preview) {
    InstrumentationModule[] modules = modules(v3Preview);
    assertThat(modules[0].instrumentationNames())
        .containsExactly("couchbase", "couchbase-2.0", "couchbase-2.0-core");
    assertThat(modules[1].instrumentationNames())
        .containsExactly(
            v3Preview
                ? new String[] {"couchbase", "couchbase-2.0", "couchbase-2.0-network-2.0"}
                : new String[] {
                  "couchbase", "couchbase-2.0", "couchbase-network-2.0", "couchbase-2.0-network"
                });
    assertThat(modules[2].instrumentationNames())
        .containsExactly(
            v3Preview
                ? new String[] {"couchbase", "couchbase-2.0", "couchbase-2.0-network-2.6"}
                : new String[] {"couchbase", "couchbase-2.6"});
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void absentSelectorsUseDefault(boolean v3Preview) {
    InstrumentationModule[] modules = modules(v3Preview);
    ConfigProperties properties = mock(ConfigProperties.class);
    when(properties.getBoolean(anyString())).thenReturn(null);
    AgentDistributionConfig config = AgentDistributionConfig.fromConfigProperties(properties);

    for (InstrumentationModule module : modules) {
      assertThat(config.isInstrumentationEnabled(module.instrumentationNames(), false)).isFalse();
      assertThat(config.isInstrumentationEnabled(module.instrumentationNames(), true)).isTrue();
    }
  }

  @ParameterizedTest
  @MethodSource("selectors")
  void selectorsAreModeDependent(
      boolean v3Preview, String selector, boolean core, boolean network, boolean network26) {
    InstrumentationModule[] modules = modules(v3Preview);
    for (boolean enabled : new boolean[] {false, true}) {
      ConfigProperties properties = mock(ConfigProperties.class);
      when(properties.getBoolean(anyString())).thenReturn(null);
      when(properties.getBoolean("otel.instrumentation." + selector + ".enabled"))
          .thenReturn(enabled);
      AgentDistributionConfig config = AgentDistributionConfig.fromConfigProperties(properties);

      assertThat(config.isInstrumentationEnabled(modules[0].instrumentationNames(), !enabled))
          .isEqualTo(core ? enabled : !enabled);
      assertThat(config.isInstrumentationEnabled(modules[1].instrumentationNames(), !enabled))
          .isEqualTo(network ? enabled : !enabled);
      assertThat(config.isInstrumentationEnabled(modules[2].instrumentationNames(), !enabled))
          .isEqualTo(network26 ? enabled : !enabled);
    }
  }

  private static Stream<Arguments> selectors() {
    return Stream.of(
        argumentSet("normal shared", false, "couchbase", true, true, true),
        argumentSet("normal 2.0 owner", false, "couchbase-2.0", true, true, false),
        argumentSet("normal core", false, "couchbase-2.0-core", true, false, false),
        argumentSet("normal pre-2.6 alias", false, "couchbase-network-2.0", false, true, false),
        argumentSet("normal pre-2.6 alias 2", false, "couchbase-2.0-network", false, true, false),
        argumentSet("normal 2.6 alias", false, "couchbase-2.6", false, false, true),
        argumentSet(
            "normal preview-only pre-2.6", false, "couchbase-2.0-network-2.0", false, false, false),
        argumentSet(
            "normal preview-only 2.6", false, "couchbase-2.0-network-2.6", false, false, false),
        argumentSet("preview shared", true, "couchbase", true, true, true),
        argumentSet("preview 2.0 owner", true, "couchbase-2.0", true, true, true),
        argumentSet("preview core", true, "couchbase-2.0-core", true, false, false),
        argumentSet("preview pre-2.6", true, "couchbase-2.0-network-2.0", false, true, false),
        argumentSet("preview 2.6", true, "couchbase-2.0-network-2.6", false, false, true),
        argumentSet("preview legacy pre-2.6", true, "couchbase-network-2.0", false, false, false),
        argumentSet("preview legacy pre-2.6 2", true, "couchbase-2.0-network", false, false, false),
        argumentSet("preview legacy 2.6", true, "couchbase-2.6", false, false, false));
  }

  @ParameterizedTest
  @CsvSource({
    "false, couchbase-2.6, couchbase-2.0-network-2.6, true",
    "true, couchbase-2.6, couchbase-2.0-network-2.6, false",
    "false, couchbase-network-2.0, couchbase-2.0-network, true",
    "false, couchbase-2.0-network, couchbase-network-2.0, false",
    "true, couchbase-network-2.0, couchbase-2.0-network-2.0, false",
    "false, couchbase-2.0, couchbase-2.6, false",
    "true, couchbase-2.0, couchbase-2.0-network-2.6, true",
    "false, couchbase, couchbase-2.0-network, true",
    "true, couchbase, couchbase-2.0-network-2.0, true"
  })
  void firstActiveSelectorWins(boolean v3Preview, String first, String second, boolean expected) {
    ConfigProperties properties = mock(ConfigProperties.class);
    when(properties.getBoolean(anyString())).thenReturn(null);
    when(properties.getBoolean("otel.instrumentation." + first + ".enabled")).thenReturn(true);
    when(properties.getBoolean("otel.instrumentation." + second + ".enabled")).thenReturn(false);
    InstrumentationModule[] modules = modules(v3Preview);
    AgentDistributionConfig config = AgentDistributionConfig.fromConfigProperties(properties);
    InstrumentationModule module =
        first.equals("couchbase-2.6")
                || second.equals("couchbase-2.6")
                || second.endsWith("network-2.6")
            ? modules[2]
            : modules[1];

    assertThat(config.isInstrumentationEnabled(module.instrumentationNames(), false))
        .isEqualTo(expected);
  }

  @Test
  void normalModeLegacySelectorsDoNotWarn() {
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
    try {
      InstrumentationModule[] modules = modules(false);
      for (String selector :
          new String[] {"couchbase-network-2.0", "couchbase-2.0-network", "couchbase-2.6"}) {
        ConfigProperties properties = mock(ConfigProperties.class);
        when(properties.getBoolean(anyString())).thenReturn(null);
        when(properties.getBoolean("otel.instrumentation." + selector + ".enabled"))
            .thenReturn(true);
        AgentDistributionConfig config = AgentDistributionConfig.fromConfigProperties(properties);
        for (InstrumentationModule module : modules) {
          config.isInstrumentationEnabled(module.instrumentationNames(), false);
        }
      }
      assertThat(records).isEmpty();
    } finally {
      logger.removeHandler(handler);
    }
  }

  private static InstrumentationModule[] modules(boolean v3Preview) {
    CommonConfig commonConfig = mock(CommonConfig.class);
    when(commonConfig.isV3Preview()).thenReturn(v3Preview);
    try (MockedStatic<AgentCommonConfig> common = mockStatic(AgentCommonConfig.class)) {
      common.when(AgentCommonConfig::get).thenReturn(commonConfig);
      return new InstrumentationModule[] {
        new CouchbaseInstrumentationModule(),
        new CouchbaseNetworkInstrumentationModule(),
        new CouchbaseNetwork26InstrumentationModule()
      };
    }
  }
}
