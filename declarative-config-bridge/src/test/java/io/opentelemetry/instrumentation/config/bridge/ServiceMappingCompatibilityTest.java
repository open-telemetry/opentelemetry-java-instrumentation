/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.config.bridge;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.incubator.semconv.service.internal.ServiceMappingResolver;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.DeclarativeConfiguration;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.OpenTelemetryConfigurationModel;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import io.opentelemetry.sdk.internal.SdkConfigProvider;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class ServiceMappingCompatibilityTest {

  @Test
  void flatLegacyUserValueWinsOverCanonicalDefault() throws Exception {
    DefaultInstrumentationConfig defaults = defaults("server_service_mapping", "default-canonical");
    Map<String, String> properties = defaults.toConfigProperties();
    properties.put("otel.instrumentation.common.peer-service-mapping", "example.com=legacy-user");

    assertResolves(flatCommon(properties), "legacy-user");
  }

  @Test
  void flatCanonicalUserValueWinsOverLegacyDefault() throws Exception {
    DefaultInstrumentationConfig defaults = defaults("service_peer_mapping", "default-legacy");
    Map<String, String> properties = defaults.toConfigProperties();
    properties.put(
        "otel.instrumentation.common.server-service-mapping", "example.com=canonical-user");

    assertResolves(flatCommon(properties), "canonical-user");
  }

  @Test
  void flatCanonicalUserValueWinsWhenBothAliasesAreConfigured() throws Exception {
    Map<String, String> properties = new HashMap<>();
    properties.put(
        "otel.instrumentation.common.server-service-mapping", "example.com=canonical-user");
    properties.put("otel.instrumentation.common.peer-service-mapping", "example.com=legacy-user");

    assertResolves(flatCommon(properties), "canonical-user");
  }

  @Test
  void flatLegacyDefaultResolves() throws Exception {
    assertResolves(
        flatCommon(defaults("service_peer_mapping", "legacy-default").toConfigProperties()),
        "legacy-default");
  }

  @Test
  void flatCanonicalDefaultResolves() throws Exception {
    assertResolves(
        flatCommon(defaults("server_service_mapping", "canonical-default").toConfigProperties()),
        "canonical-default");
  }

  @Test
  void declarativeLegacyUserValueWinsOverCanonicalDefault() throws Exception {
    assertDeclarativeUserWins(
        "service_peer_mapping", "peer", "legacy-user", "server_service_mapping");
  }

  @Test
  void declarativeCanonicalUserValueWinsOverLegacyDefault() throws Exception {
    assertDeclarativeUserWins(
        "server_service_mapping", "target", "canonical-user", "service_peer_mapping");
  }

  @Test
  void declarativeCanonicalUserValueWinsWhenBothAliasesAreConfigured() throws Exception {
    String yaml =
        "file_format: \"1.0\"\n"
            + "instrumentation/development:\n"
            + "  java:\n"
            + "    common:\n"
            + "      server_service_mapping:\n"
            + "        - target: example.com\n"
            + "          service_name: canonical-user\n"
            + "      service_peer_mapping:\n"
            + "        - peer: example.com\n"
            + "          service_name: legacy-user\n";
    assertResolves(declarativeCommon(parseModel(yaml)), "canonical-user");
  }

  @Test
  void declarativeLegacyDefaultResolves() throws Exception {
    OpenTelemetryConfigurationModel model = parseModel("file_format: \"1.0\"\n");
    defaults("service_peer_mapping", "legacy-default").applyToModel(model);

    assertResolves(declarativeCommon(model), "legacy-default");
  }

  @Test
  void declarativeCanonicalDefaultResolves() throws Exception {
    OpenTelemetryConfigurationModel model = parseModel("file_format: \"1.0\"\n");
    defaults("server_service_mapping", "canonical-default").applyToModel(model);

    assertResolves(declarativeCommon(model), "canonical-default");
  }

  @Test
  void explicitEmptyCanonicalListSuppressesLegacyDefault() throws Exception {
    assertExplicitEmptySuppressesDefault("server_service_mapping", "service_peer_mapping");
  }

  @Test
  void explicitEmptyLegacyListSuppressesCanonicalDefault() throws Exception {
    assertExplicitEmptySuppressesDefault("service_peer_mapping", "server_service_mapping");
  }

  @Test
  void nullAliasDoesNotSuppressDefault() throws Exception {
    String yaml =
        "file_format: \"1.0\"\n"
            + "instrumentation/development:\n"
            + "  java:\n"
            + "    common:\n"
            + "      service_peer_mapping: null\n";
    OpenTelemetryConfigurationModel model = parseModel(yaml);
    defaults("server_service_mapping", "canonical-default").applyToModel(model);

    assertResolves(declarativeCommon(model), "canonical-default");
  }

  private static void assertExplicitEmptySuppressesDefault(String userPath, String defaultPath)
      throws Exception {
    String yaml =
        "file_format: \"1.0\"\n"
            + "instrumentation/development:\n"
            + "  java:\n"
            + "    common:\n"
            + "      "
            + userPath
            + ": []\n";
    OpenTelemetryConfigurationModel model = parseModel(yaml);
    defaults(defaultPath, "default-value").applyToModel(model);

    DeclarativeConfigProperties common = declarativeCommon(model);
    assertThat(common.getPropertyKeys()).contains(userPath);
    // An empty structured list has no equivalent flat property value, but it still suppresses the
    // alias default before the model is serialized to flat config.
    assertResolves(common, null);
  }

  private static void assertDeclarativeUserWins(
      String userPath, String userField, String userValue, String defaultPath) throws Exception {
    String yaml =
        "file_format: \"1.0\"\n"
            + "instrumentation/development:\n"
            + "  java:\n"
            + "    common:\n"
            + "      "
            + userPath
            + ":\n"
            + "        - "
            + userField
            + ": example.com\n"
            + "          service_name: "
            + userValue
            + "\n";
    OpenTelemetryConfigurationModel model = parseModel(yaml);
    defaults(defaultPath, "default-value").applyToModel(model);

    assertResolves(declarativeCommon(model), userValue);
  }

  private static DefaultInstrumentationConfig defaults(String path, String serviceName) {
    Map<String, String> mapping = new HashMap<>();
    mapping.put(path.equals("service_peer_mapping") ? "peer" : "target", "example.com");
    mapping.put("service_name", serviceName);
    return new DefaultInstrumentationConfig()
        .get("common")
        .setDefault(path, singletonList(mapping));
  }

  private static DeclarativeConfigProperties flatCommon(Map<String, String> properties) {
    DeclarativeConfigProperties instrumentation =
        ConfigPropertiesBackedDeclarativeConfigProperties.createInstrumentationConfig(
            DefaultConfigProperties.createFromMap(properties));
    return instrumentation.get("java").get("common");
  }

  private static OpenTelemetryConfigurationModel parseModel(String yaml) {
    return DeclarativeConfiguration.parse(new ByteArrayInputStream(yaml.getBytes(UTF_8)));
  }

  private static DeclarativeConfigProperties declarativeCommon(
      OpenTelemetryConfigurationModel model) {
    DeclarativeConfigProperties instrumentation =
        SdkConfigProvider.create(DeclarativeConfiguration.toConfigProperties(model))
            .getInstrumentationConfig();
    return instrumentation.get("java").get("common");
  }

  private static void assertResolves(DeclarativeConfigProperties common, String expected)
      throws Exception {
    OpenTelemetry openTelemetry = openTelemetry(common);
    ServiceMappingResolver resolver = new ServiceMappingResolver(openTelemetry);
    Map<String, String> attributes = new HashMap<>();
    BiConsumer<AttributeKey<String>, String> setter =
        (key, value) -> attributes.put(key.getKey(), value);
    resolver.resolve("example.com", null, (Supplier<String>) () -> null, setter);

    if (expected == null) {
      assertThat(attributes).isEmpty();
    } else {
      assertThat(attributes).containsEntry("server.service.name", expected);
    }
  }

  private static OpenTelemetry openTelemetry(DeclarativeConfigProperties common) {
    return (OpenTelemetry)
        Proxy.newProxyInstance(
            ExtendedOpenTelemetry.class.getClassLoader(),
            new Class<?>[] {ExtendedOpenTelemetry.class},
            (proxy, method, arguments) -> {
              if (method.getName().equals("getInstrumentationConfig")
                  && arguments != null
                  && arguments.length == 1
                  && arguments[0].equals("common")) {
                return common;
              }
              if (method.getName().equals("getGeneralInstrumentationConfig")) {
                return DeclarativeConfigProperties.empty();
              }
              if (method.getName().equals("toString")) {
                return "service-mapping-test-otel";
              }
              if (method.getName().equals("hashCode")) {
                return System.identityHashCode(proxy);
              }
              if (method.getName().equals("equals")) {
                return proxy == arguments[0];
              }
              return null;
            });
  }
}
