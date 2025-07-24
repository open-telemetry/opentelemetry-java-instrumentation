/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension.internal;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.extension.incubator.fileconfig.DeclarativeConfiguration;
import io.opentelemetry.sdk.extension.incubator.fileconfig.SdkConfigProvider;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.InstrumentationModel;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.OpenTelemetryConfigurationModel;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DeclarativeConfigPropertiesBridgeTest {

  private ConfigProperties bridge;
  private ConfigProperties emptyBridge;

  @BeforeEach
  void setup() {
    bridge = create(new DeclarativeConfigPropertiesBridgeBuilder());

    OpenTelemetryConfigurationModel emptyModel =
        new OpenTelemetryConfigurationModel()
            .withAdditionalProperty("instrumentation/development", new InstrumentationModel());
    SdkConfigProvider emptyConfigProvider = SdkConfigProvider.create(emptyModel);
    emptyBridge =
        new DeclarativeConfigPropertiesBridgeBuilder()
            .resolveInstrumentationConfig(
                Objects.requireNonNull(emptyConfigProvider.getInstrumentationConfig()));
  }

  private static ConfigProperties create(
      DeclarativeConfigPropertiesBridgeBuilder builder) {
    OpenTelemetryConfigurationModel model =
        DeclarativeConfiguration.parse(
            DeclarativeConfigPropertiesBridgeTest.class
                .getClassLoader()
                .getResourceAsStream("config.yaml"));
    return builder.resolveInstrumentationConfig(
        SdkConfigProvider.create(model).getInstrumentationConfig());
  }

  @Test
  void getProperties() {
    // only properties starting with "otel.instrumentation." are resolved
    // asking for properties which don't exist or inaccessible shouldn't result in an error
    assertThat(bridge.getString("file_format")).isNull();
    assertThat(bridge.getString("file_format", "foo")).isEqualTo("foo");
    assertThat(emptyBridge.getBoolean("otel.instrumentation.common.default-enabled")).isNull();
    assertThat(emptyBridge.getBoolean("otel.instrumentation.common.default-enabled", true))
        .isTrue();

    // common cases
    assertThat(bridge.getBoolean("otel.instrumentation.runtime-telemetry.enabled")).isFalse();

    // check all the types
    Map<String, String> expectedMap = new HashMap<>();
    expectedMap.put("string_key1", "value1");
    expectedMap.put("string_key2", "value2");
    assertThat(bridge.getString("otel.instrumentation.example-instrumentation.string_key"))
        .isEqualTo("value");
    assertThat(bridge.getBoolean("otel.instrumentation.example-instrumentation.bool_key")).isTrue();
    assertThat(bridge.getInt("otel.instrumentation.example-instrumentation.int_key")).isEqualTo(1);
    assertThat(bridge.getLong("otel.instrumentation.example-instrumentation.int_key"))
        .isEqualTo(1L);
    assertThat(bridge.getDuration("otel.instrumentation.example-instrumentation.int_key"))
        .isEqualTo(Duration.ofMillis(1));
    assertThat(bridge.getDouble("otel.instrumentation.example-instrumentation.double_key"))
        .isEqualTo(1.1);
    assertThat(bridge.getList("otel.instrumentation.example-instrumentation.list_key"))
        .isEqualTo(Arrays.asList("value1", "value2"));
    assertThat(bridge.getMap("otel.instrumentation.example-instrumentation.map_key"))
        .isEqualTo(expectedMap);

    // asking for properties with the wrong type returns null
    assertThat(bridge.getBoolean("otel.instrumentation.example-instrumentation.string_key"))
        .isNull();
    assertThat(bridge.getString("otel.instrumentation.example-instrumentation.bool_key")).isNull();
    assertThat(bridge.getString("otel.instrumentation.example-instrumentation.int_key")).isNull();
    assertThat(bridge.getString("otel.instrumentation.example-instrumentation.double_key"))
        .isNull();
    assertThat(bridge.getString("otel.instrumentation.example-instrumentation.list_key")).isNull();
    assertThat(bridge.getString("otel.instrumentation.example-instrumentation.map_key")).isNull();

    // check all the types
    assertThat(bridge.getString("otel.instrumentation.other-instrumentation.string_key", "value"))
        .isEqualTo("value");
    assertThat(bridge.getBoolean("otel.instrumentation.other-instrumentation.bool_key", true))
        .isTrue();
    assertThat(bridge.getInt("otel.instrumentation.other-instrumentation.int_key", 1)).isEqualTo(1);
    assertThat(bridge.getLong("otel.instrumentation.other-instrumentation.int_key", 1L))
        .isEqualTo(1L);
    assertThat(
            bridge.getDuration(
                "otel.instrumentation.other-instrumentation.int_key", Duration.ofMillis(1)))
        .isEqualTo(Duration.ofMillis(1));
    assertThat(bridge.getDouble("otel.instrumentation.other-instrumentation.double_key", 1.1))
        .isEqualTo(1.1);
    assertThat(
            bridge.getList(
                "otel.instrumentation.other-instrumentation.list_key",
                Arrays.asList("value1", "value2")))
        .isEqualTo(Arrays.asList("value1", "value2"));
    assertThat(bridge.getMap("otel.instrumentation.other-instrumentation.map_key", expectedMap))
        .isEqualTo(expectedMap);
  }

  @Test
  void vendor() {
    // verify vendor specific property names are preserved in unchanged form (prefix is not stripped
    // as for otel.instrumentation.*)
    assertThat(bridge.getBoolean("acme.full_name.preserved")).isTrue();
  }

  @Test
  void vendorTranslation() {
    ConfigProperties propertiesBridge =
        create(new DeclarativeConfigPropertiesBridgeBuilder().addTranslation("acme", "acme.full_name"));
    assertThat(propertiesBridge.getBoolean("acme.preserved")).isTrue();
  }

  @Test
  void agentCommonTranslation() {
    assertThat(
            create(
                    new DeclarativeConfigPropertiesBridgeBuilder()
                        .addTranslation(
                            "otel.instrumentation.common.default-enabled",
                            "common.default.enabled"))
                .getBoolean("otel.instrumentation.common.default-enabled"))
        .isFalse();
  }

  @Test
  void agentTranslation() {
    ConfigProperties bridge =
        create(
            new DeclarativeConfigPropertiesBridgeBuilder()
                .addTranslation("otel.javaagent", "agent")
                .addFixedValue("otel.javaagent.debug", true)
                .addFixedValue("otel.javaagent.logging", "application"));

    assertThat(bridge.getBoolean("otel.javaagent.debug")).isTrue();
    assertThat(bridge.getBoolean("otel.javaagent.experimental.indy")).isTrue();
    assertThat(bridge.getString("otel.javaagent.logging")).isEqualTo("application");
  }
}
