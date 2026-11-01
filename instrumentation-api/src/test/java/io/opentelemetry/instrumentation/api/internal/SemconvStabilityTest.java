/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldServicePeerSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitPreviewServicePeerSemconv;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.common.ComponentLoader;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.DeclarativeConfiguration;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.OpenTelemetryConfigurationModel;
import io.opentelemetry.sdk.internal.SdkConfigProvider;
import java.io.ByteArrayInputStream;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junitpioneer.jupiter.SetSystemProperty;

class SemconvStabilityTest {

  @Test
  void resolveStringListUsesDeclarativeListBeforeFallback() {
    // general:
    //   semconv_stability:
    //     opt_in: [database]
    DeclarativeConfigProperties semconvStability = general(property("opt_in", asList("database")));

    // otel.semconv-stability.opt-in=code
    assertThat(
            SemconvSelectionResolver.resolveStringListWithFallbackValue(
                semconvStability, "opt_in", "code"))
        .containsExactly("database");
  }

  @Test
  void resolveStringListEmptyDeclarativeListDisablesFallback() {
    // general:
    //   semconv_stability:
    //     opt_in: []
    DeclarativeConfigProperties semconvStability = general(property("opt_in", emptyList()));

    // otel.semconv-stability.opt-in=code
    assertThat(
            SemconvSelectionResolver.resolveStringListWithFallbackValue(
                semconvStability, "opt_in", "code"))
        .isEmpty();
  }

  @Test
  void resolveStringListUsesFallbackWhenDeclarativeListIsAbsent() {
    DeclarativeConfigProperties semconvStability = general();

    // otel.semconv-stability.opt-in=database,code
    assertThat(
            SemconvSelectionResolver.resolveStringListWithFallbackValue(
                semconvStability, "opt_in", "database, code"))
        .containsExactlyInAnyOrder("database", "code");
  }

  @Test
  void parseCommaSeparatedSet_ignoresBlankEntries() {
    assertThat(SemconvSelectionResolver.parseCommaSeparatedSet("service.peer, ,service.peer/dup,"))
        .containsExactlyInAnyOrder("service.peer", "service.peer/dup");
  }

  @ParameterizedTest
  @MethodSource("previewSelections")
  @SetSystemProperty(key = "otel.semconv-stability.opt-in", value = "")
  @SetSystemProperty(key = "otel.semconv-stability.preview", value = "")
  void previewSelectionFromFlatConfig(String optIn, String preview, SemconvMode expected) {
    System.setProperty("otel.semconv-stability.opt-in", optIn);
    System.setProperty("otel.semconv-stability.preview", preview);
    OpenTelemetry openTelemetry = OpenTelemetry.noop();
    SemconvSelectionResolver resolver = new SemconvSelectionResolver(openTelemetry);

    assertThat(resolver.servicePeer()).isEqualTo(expected);
  }

  @Test
  @SetSystemProperty(key = "otel.semconv-stability.preview", value = "service.peer")
  void declarativePreviewOverridesSystemPropertyFallback() {
    String yaml =
        "file_format: 1.1\n"
            + "instrumentation/development:\n"
            + "  java:\n"
            + "    common:\n"
            + "      semconv_stability:\n"
            + "        preview: [service.peer/dup]\n";
    OpenTelemetryConfigurationModel model =
        DeclarativeConfiguration.parse(new ByteArrayInputStream(yaml.getBytes(UTF_8)));
    ConfigProvider configProvider =
        SdkConfigProvider.create(DeclarativeConfiguration.toConfigProperties(model));

    OpenTelemetry openTelemetry = openTelemetry(configProvider);
    assertThat(emitOldServicePeerSemconv(openTelemetry)).isTrue();
    assertThat(emitPreviewServicePeerSemconv(openTelemetry)).isTrue();
  }

  @Test
  @SetSystemProperty(key = "otel.semconv-stability.preview", value = "service.peer/dup")
  void emptyDeclarativePreviewDisablesSystemPropertyFallback() {
    String yaml =
        "file_format: 1.1\n"
            + "instrumentation/development:\n"
            + "  java:\n"
            + "    common:\n"
            + "      semconv_stability:\n"
            + "        preview: []\n";
    OpenTelemetryConfigurationModel model =
        DeclarativeConfiguration.parse(new ByteArrayInputStream(yaml.getBytes(UTF_8)));
    ConfigProvider configProvider =
        SdkConfigProvider.create(DeclarativeConfiguration.toConfigProperties(model));

    OpenTelemetry openTelemetry = openTelemetry(configProvider);
    assertThat(emitOldServicePeerSemconv(openTelemetry)).isTrue();
    assertThat(emitPreviewServicePeerSemconv(openTelemetry)).isFalse();
  }

  private static Stream<Arguments> previewSelections() {
    return Stream.of(
        Arguments.of("", "", SemconvMode.V0_STABLE),
        Arguments.of("service.peer", "", SemconvMode.V0_STABLE),
        Arguments.of("service.peer/dup", "", SemconvMode.V0_STABLE),
        Arguments.of("", "service.peer", SemconvMode.V1_EXPERIMENTAL),
        Arguments.of("", "service.peer/dup", SemconvMode.V1_EXPERIMENTAL.withDualEmit()),
        Arguments.of("service.peer/dup", "service.peer", SemconvMode.V1_EXPERIMENTAL),
        Arguments.of(
            "service.peer", "service.peer/dup", SemconvMode.V1_EXPERIMENTAL.withDualEmit()),
        Arguments.of(
            "", "service.peer,service.peer/dup", SemconvMode.V1_EXPERIMENTAL.withDualEmit()));
  }

  private static OpenTelemetry openTelemetry(ConfigProvider configProvider) {
    ExtendedOpenTelemetry openTelemetry = mock(ExtendedOpenTelemetry.class);
    when(openTelemetry.getGeneralInstrumentationConfig())
        .thenReturn(configProvider.getGeneralInstrumentationConfig());
    when(openTelemetry.getInstrumentationConfig("common"))
        .thenReturn(configProvider.getInstrumentationConfig("common"));
    return openTelemetry;
  }

  @SafeVarargs
  private static DeclarativeConfigProperties general(Entry<String, Object>... entries) {
    Map<String, Object> result = new HashMap<String, Object>();
    for (Entry<String, Object> entry : entries) {
      result.put(entry.getKey(), entry.getValue());
    }
    return new TestDeclarativeConfigProperties(result);
  }

  private static Entry<String, Object> property(String name, Object value) {
    return new SimpleImmutableEntry<String, Object>(name, value);
  }

  private static final class TestDeclarativeConfigProperties
      implements DeclarativeConfigProperties {

    private final Map<String, Object> values;

    private TestDeclarativeConfigProperties(Map<String, Object> values) {
      this.values = values;
    }

    @Override
    public String getString(String name) {
      Object value = values.get(name);
      return value instanceof String ? (String) value : null;
    }

    @Override
    public Boolean getBoolean(String name) {
      Object value = values.get(name);
      return value instanceof Boolean ? (Boolean) value : null;
    }

    @Override
    public Integer getInt(String name) {
      Object value = values.get(name);
      return value instanceof Integer ? (Integer) value : null;
    }

    @Override
    public Long getLong(String name) {
      Object value = values.get(name);
      return value instanceof Long ? (Long) value : null;
    }

    @Override
    public Double getDouble(String name) {
      Object value = values.get(name);
      return value instanceof Double ? (Double) value : null;
    }

    @Override
    public boolean isString(String name) {
      return getString(name) != null;
    }

    @Override
    public boolean isBoolean(String name) {
      return getBoolean(name) != null;
    }

    @Override
    public boolean isInt(String name) {
      return getInt(name) != null;
    }

    @Override
    public boolean isLong(String name) {
      return getLong(name) != null;
    }

    @Override
    public boolean isDouble(String name) {
      return getDouble(name) != null;
    }

    @Override
    public <T> List<T> getScalarList(String name, Class<T> type) {
      Object value = values.get(name);
      if (!(value instanceof List<?>)) {
        return emptyList();
      }
      return ((List<?>) value)
          .stream().filter(type::isInstance).map(type::cast).collect(Collectors.<T>toList());
    }

    @Override
    public DeclarativeConfigProperties getStructured(String name) {
      Object value = values.get(name);
      if (value instanceof DeclarativeConfigProperties) {
        return (DeclarativeConfigProperties) value;
      }
      if (value instanceof Map<?, ?>) {
        @SuppressWarnings("unchecked")
        Map<String, Object> nested = (Map<String, Object>) value;
        return new TestDeclarativeConfigProperties(nested);
      }
      return DeclarativeConfigProperties.empty();
    }

    @Override
    public List<DeclarativeConfigProperties> getStructuredList(String name) {
      return emptyList();
    }

    @Override
    public Set<String> getPropertyKeys() {
      return values.keySet();
    }

    @Override
    public ComponentLoader getComponentLoader() {
      return ComponentLoader.forClassLoader(getClass().getClassLoader());
    }
  }
}
