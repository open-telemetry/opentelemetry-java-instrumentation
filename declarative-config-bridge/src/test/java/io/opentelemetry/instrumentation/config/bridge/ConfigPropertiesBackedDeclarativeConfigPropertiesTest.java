/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.config.bridge;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConfigPropertiesBackedDeclarativeConfigPropertiesTest {

  @Test
  void testTranslateName_regularName() {
    DeclarativeConfigProperties config =
        createConfig("otel.instrumentation.kafka.producer-propagation.enabled", "false");

    assertThat(
            config
                .getStructured("java")
                .getStructured("kafka")
                .getStructured("producer_propagation")
                .getBoolean("enabled"))
        .isNotNull()
        .isFalse();
  }

  @Test
  void testTranslateName_withDevelopmentSuffix_noExperimental() {
    DeclarativeConfigProperties config =
        createConfig(
            "otel.instrumentation.common.experimental.controller-telemetry.enabled", "true");

    assertThat(
            config
                .getStructured("java")
                .getStructured("common")
                .getStructured("controller_telemetry/development")
                .getBoolean("enabled"))
        .isNotNull()
        .isTrue();
  }

  @Test
  void testTranslateName_withDevelopmentSuffix_alreadyHasExperimental() {
    DeclarativeConfigProperties config =
        createConfig("otel.instrumentation.grpc.experimental-span-attributes", "true");

    assertThat(
            config
                .getStructured("java")
                .getStructured("grpc")
                .getBoolean("experimental_span_attributes/development"))
        .isNotNull()
        .isTrue();
  }

  @Test
  void testTranslateName_withExperimentalInMiddle() {
    DeclarativeConfigProperties config =
        createConfig("otel.instrumentation.http.client.emit-experimental-telemetry", "true");

    assertThat(
            config
                .getStructured("java")
                .getStructured("http")
                .getStructured("client")
                .getBoolean("emit_experimental_telemetry/development"))
        .isNotNull()
        .isTrue();
  }

  @Test
  void testAgentPrefix() {
    DeclarativeConfigProperties config = createConfig("otel.javaagent.experimental.indy", "true");

    assertThat(config.getStructured("java").getStructured("agent").getBoolean("indy/development"))
        .isNotNull()
        .isTrue();
  }

  @Test
  void testJmxPrefix() {
    DeclarativeConfigProperties config = createConfig("otel.jmx.enabled", "true");

    assertThat(config.getStructured("java").getStructured("jmx").getBoolean("enabled"))
        .isNotNull()
        .isTrue();
  }

  @Test
  void testGeneralHttpListMapping() {
    DeclarativeConfigProperties config =
        createConfig("otel.instrumentation.http.client.capture-request-headers", "header1,header2");

    assertThat(
            config
                .getStructured("general")
                .getStructured("http")
                .getStructured("client")
                .getScalarList("request_captured_headers", String.class))
        .containsExactly("header1", "header2");
  }

  @Test
  void testGeneralPeerServiceMapping() {
    DeclarativeConfigProperties config =
        createConfig("otel.instrumentation.common.peer-service-mapping", "old-name=new-name");

    assertThat(
            config
                .getStructured("general")
                .getStructured("peer")
                .getStructuredList("service_mapping"))
        .isNotNull();
  }

  @Test
  void testGetInt() {
    DeclarativeConfigProperties config =
        createConfig("otel.instrumentation.aws-lambda.flush-timeout", "5000");

    assertThat(config.getStructured("java").getStructured("aws_lambda").getInt("flush_timeout"))
        .isEqualTo(5000);
  }

  @Test
  void testGetLong() {
    assertThat(
            createConfig("otel.instrumentation.aws-lambda.flush-timeout", "30000")
                .getStructured("java")
                .getStructured("aws_lambda")
                .getLong("flush_timeout"))
        .isEqualTo(30000L);

    // special case: duration string
    assertThat(
            createConfig("otel.jmx.discovery.delay", "30s")
                .getStructured("java")
                .getStructured("jmx")
                .getStructured("discovery")
                .getLong("delay"))
        .isEqualTo(30000L);
    assertThat(
            createConfig("otel.metric.export.interval", "30s")
                .getStructured("java")
                .getStructured("jmx")
                .getStructured("discovery")
                .getLong("delay"))
        .isEqualTo(30000L);
  }

  @Test
  void testGetDouble() {
    DeclarativeConfigProperties config = createConfig("otel.instrumentation.test.ratio", "0.5");

    assertThat(config.getStructured("java").getStructured("test").getDouble("ratio"))
        .isEqualTo(0.5);
  }

  @Test
  void testGetScalarList_emptyList() {
    DeclarativeConfigProperties config = createConfig("nomatch", "value");

    assertThat(
            config.getStructured("java").getStructured("test").getScalarList("items", String.class))
        .isNull();
  }

  @Test
  void testGetScalarList_nonStringType() {
    DeclarativeConfigProperties config = createConfig("nomatch", "value");

    assertThat(
            config
                .getStructured("java")
                .getStructured("test")
                .getScalarList("items", Integer.class))
        .isNull();
  }

  @Test
  void testPathWithJavaPrefix() {
    DeclarativeConfigProperties config =
        createConfig("otel.instrumentation.kafka.experimental-span-attributes", "true");

    assertThat(
            config
                .getStructured("java")
                .getStructured("kafka")
                .getBoolean("experimental_span_attributes/development"))
        .isTrue();
  }

  @Test
  void testUnderscoreToDashConversion() {
    DeclarativeConfigProperties config =
        createConfig("otel.instrumentation.spring-scheduling.test-property", "value");

    assertThat(
            config
                .getStructured("java")
                .getStructured("spring_scheduling")
                .getString("test_property"))
        .isEqualTo("value");
  }

  @Test
  void testGetPropertyKeys() {
    DeclarativeConfigProperties config = createConfig("nomatch", "value");

    assertThat(config.getPropertyKeys()).isEmpty();
  }

  @Test
  void testGetComponentLoader() {
    DeclarativeConfigProperties config = createConfig("nomatch", "value");

    assertThat(config.getComponentLoader()).isNotNull();
  }

  @Test
  void testWithoutJavaPrefix_doesNotMatch() {
    DeclarativeConfigProperties config =
        createConfig("otel.instrumentation.kafka.producer-propagation.enabled", "false");

    // Without "java" prefix, should not match the property
    assertThat(
            config
                .getStructured("kafka")
                .getStructured("producer_propagation")
                .getBoolean("enabled"))
        .isNull();
  }

  @Test
  void testAgentInstrumentationMode_getString_booleanTrue() {
    DeclarativeConfigProperties config =
        createConfig("otel.instrumentation.common.default-enabled", "true");

    assertThat(
            config.getStructured("java").getStructured("agent").getString("instrumentation_mode"))
        .isEqualTo("default");
  }

  @Test
  void testAgentInstrumentationMode_getString_booleanFalse() {
    DeclarativeConfigProperties config =
        createConfig("otel.instrumentation.common.default-enabled", "false");

    assertThat(
            config.getStructured("java").getStructured("agent").getString("instrumentation_mode"))
        .isEqualTo("none");
  }

  @Test
  void testSpringStarterInstrumentationMode_getString_booleanTrue() {
    DeclarativeConfigProperties config =
        createConfig("otel.instrumentation.common.default-enabled", "true");

    assertThat(
            config
                .getStructured("java")
                .getStructured("spring_starter")
                .getString("instrumentation_mode"))
        .isEqualTo("default");
  }

  @Test
  void testSpringStarterInstrumentationMode_getString_booleanFalse() {
    DeclarativeConfigProperties config =
        createConfig("otel.instrumentation.common.default-enabled", "false");

    assertThat(
            config
                .getStructured("java")
                .getStructured("spring_starter")
                .getString("instrumentation_mode"))
        .isEqualTo("none");
  }

  @Test
  void testAgentInstrumentationMode_notSet() {
    DeclarativeConfigProperties config = createConfig("some.other.property", "value");

    assertThat(
            config.getStructured("java").getStructured("agent").getString("instrumentation_mode"))
        .isNull();
  }

  private static DeclarativeConfigProperties createConfig(String key, String value) {
    Map<String, String> properties = new HashMap<>();
    properties.put(key, value);
    return ConfigPropertiesBackedDeclarativeConfigProperties.createInstrumentationConfig(
        DefaultConfigProperties.createFromMap(properties));
  }
}
