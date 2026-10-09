/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldRpcSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldServicePeerSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitPreviewRpcSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitPreviewServicePeerSemconv;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
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
import io.opentelemetry.semconv.SchemaUrls;
import java.io.ByteArrayInputStream;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.HashMap;
import java.util.HashSet;
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
  void resolveGeneralStableFlags_parsesCommaSeparatedList() {
    // general:
    //   stability_opt_in_list: "database/dup, code, service.peer"
    DeclarativeConfigProperties general =
        general(stabilityOptInList(" database/dup, code , service.peer "));

    assertThat(SemconvSelectionResolver.resolveGeneralStableFlags(general))
        .containsExactlyInAnyOrder("database/dup", "code", "service.peer");
  }

  @Test
  void stableOptInListTakesPrecedenceOverFallback() {
    // general:
    //   stability_opt_in_list: "database"
    DeclarativeConfigProperties general = general(stabilityOptInList("database"));

    assertThat(SemconvSelectionResolver.resolveStableOptInValues(general, stableOptIn("code")))
        .containsExactly("database");
  }

  @Test
  void emptyStableOptInListDisablesFallback() {
    // general:
    //   stability_opt_in_list: ""
    DeclarativeConfigProperties general = general(stabilityOptInList(""));

    assertThat(SemconvSelectionResolver.resolveStableOptInValues(general, stableOptIn("code")))
        .isEmpty();
  }

  @Test
  void absentStableOptInListUsesFallback() {
    DeclarativeConfigProperties general = general();

    assertThat(SemconvSelectionResolver.resolveStableOptInValues(general, stableOptIn("code")))
        .containsExactly("code");
  }

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
    assertThat(SemconvSelectionResolver.parseCommaSeparatedSet("rpc, service.peer, ,database/dup,"))
        .containsExactlyInAnyOrder("rpc", "service.peer", "database/dup");
  }

  @Test
  void explicitDomainConfigTakesPrecedenceOverPreview() {
    // general:
    //   rpc:
    //     semconv:
    //       version: 1
    //       experimental: true
    //       dual_emit: true
    DeclarativeConfigProperties general = general(domainSemconv("rpc", 1, true, true));

    // otel.semconv-stability.preview=rpc
    SemconvMode rpc = new SemconvSelectionResolver(general, noStableOptIn(), preview("rpc")).rpc();

    assertThat(rpc).isEqualTo(SemconvMode.V1_EXPERIMENTAL.withDualEmit());
  }

  @Test
  void unsupportedExplicitDomainVersionFallsBackToDefault() {
    // general:
    //   rpc:
    //     semconv:
    //       version: 1
    DeclarativeConfigProperties general = general(domainSemconv("rpc", 1));
    SemconvSelectionResolver resolver =
        new SemconvSelectionResolver(general, noStableOptIn(), noPreview());
    SemconvMode rpc = resolver.rpc();

    assertThat(rpc).isEqualTo(SemconvMode.V0_STABLE);
  }

  @Test
  void experimentalDomainVersionAppliesToPreviewDomains() {
    // general:
    //   rpc:
    //     semconv:
    //       version: 1
    //       experimental: true
    DeclarativeConfigProperties general = general(domainSemconv("rpc", 1, true, false));
    SemconvSelectionResolver resolver =
        new SemconvSelectionResolver(general, noStableOptIn(), noPreview());
    SemconvMode rpc = resolver.rpc();

    assertThat(rpc).isEqualTo(SemconvMode.V1_EXPERIMENTAL);
  }

  @Test
  void explicitDomainVersionZeroMeansOldOnlyEvenWithDualEmit() {
    // general:
    //   rpc:
    //     semconv:
    //       version: 0
    //       dual_emit: true
    DeclarativeConfigProperties general = general(domainSemconv("rpc", 0, true));
    SemconvMode rpc =
        new SemconvSelectionResolver(general, noStableOptIn(), preview("rpc/dup")).rpc();

    assertThat(rpc).isEqualTo(SemconvMode.V0_STABLE);
  }

  @Test
  void previewFallbackAppliesToPreviewDomains() {
    // java:
    //   common:
    //     semconv_stability:
    //       preview: [rpc, service.peer]
    DeclarativeConfigProperties general = general();
    // otel.semconv-stability.preview=rpc,service.peer
    Set<String> preview = preview("rpc", "service.peer");

    SemconvSelectionResolver resolver =
        new SemconvSelectionResolver(general, noStableOptIn(), preview);
    SemconvMode rpc = resolver.rpc();
    SemconvMode servicePeer = resolver.servicePeer();

    assertThat(rpc).isEqualTo(SemconvMode.V1_EXPERIMENTAL);
    assertThat(servicePeer).isEqualTo(SemconvMode.V1_EXPERIMENTAL);
  }

  @Test
  void messagingUsesAdoptedSchemaUrl() {
    assertThat(SemconvStability.messagingSchemaUrl()).isEqualTo(SchemaUrls.V1_43_0);
  }

  @Test
  void domainsResolveIndependently() {
    SemconvSelectionResolver resolver =
        new SemconvSelectionResolver(
            general(), stableOptIn("rpc", "service.peer"), preview("rpc/dup", "service.peer/dup"));

    assertThat(resolver.rpc()).isEqualTo(SemconvMode.V1_EXPERIMENTAL.withDualEmit());
    assertThat(resolver.servicePeer()).isEqualTo(SemconvMode.V1_EXPERIMENTAL.withDualEmit());
  }

  @ParameterizedTest
  @MethodSource("previewSelections")
  @SetSystemProperty(key = "otel.semconv-stability.opt-in", value = "")
  @SetSystemProperty(key = "otel.semconv-stability.preview", value = "")
  void previewSelectionFromFlatConfig(
      String domain, String optIn, String preview, SemconvMode expected) {
    System.setProperty("otel.semconv-stability.opt-in", optIn);
    System.setProperty("otel.semconv-stability.preview", preview);
    OpenTelemetry openTelemetry = OpenTelemetry.noop();
    SemconvSelectionResolver resolver = new SemconvSelectionResolver(openTelemetry, general());

    assertThat(domain.equals("rpc") ? resolver.rpc() : resolver.servicePeer()).isEqualTo(expected);
  }

  @ParameterizedTest
  @MethodSource("previewSelections")
  void previewSelectionFromDeclarativeConfig(
      String domain, String optIn, String preview, SemconvMode expected) {
    String yaml =
        "file_format: 1.1\n"
            + "instrumentation/development:\n"
            + "  general:\n"
            + "    stability_opt_in_list: '"
            + optIn
            + "'\n"
            + "  java:\n"
            + "    common:\n"
            + "      semconv_stability:\n"
            + "        preview: ["
            + preview
            + "]\n";
    OpenTelemetryConfigurationModel model =
        DeclarativeConfiguration.parse(new ByteArrayInputStream(yaml.getBytes(UTF_8)));
    ConfigProvider configProvider =
        SdkConfigProvider.create(DeclarativeConfiguration.toConfigProperties(model));

    OpenTelemetry openTelemetry = openTelemetry(configProvider);
    assertThat(
            domain.equals("rpc")
                ? emitOldRpcSemconv(openTelemetry)
                : emitOldServicePeerSemconv(openTelemetry))
        .isEqualTo(expected.version() == 0 || expected.dualEmit());
    assertThat(
            domain.equals("rpc")
                ? emitPreviewRpcSemconv(openTelemetry)
                : emitPreviewServicePeerSemconv(openTelemetry))
        .isEqualTo(expected.version() >= 1);
  }

  @ParameterizedTest
  @MethodSource("structuredRpcSelections")
  void structuredRpcSelectionFromDeclarativeConfig(
      int version, boolean experimental, boolean dualEmit, SemconvMode expected) {
    String yaml =
        "file_format: 1.1\n"
            + "instrumentation/development:\n"
            + "  general:\n"
            + "    rpc:\n"
            + "      semconv:\n"
            + "        version: "
            + version
            + "\n"
            + "        experimental: "
            + experimental
            + "\n"
            + "        dual_emit: "
            + dualEmit
            + "\n"
            + "  java:\n"
            + "    common:\n"
            + "      semconv_stability:\n"
            + "        preview: [rpc/dup]\n";
    OpenTelemetryConfigurationModel model =
        DeclarativeConfiguration.parse(new ByteArrayInputStream(yaml.getBytes(UTF_8)));
    ConfigProvider configProvider =
        SdkConfigProvider.create(DeclarativeConfiguration.toConfigProperties(model));

    OpenTelemetry openTelemetry = openTelemetry(configProvider);
    assertThat(emitOldRpcSemconv(openTelemetry))
        .isEqualTo(expected.version() == 0 || expected.dualEmit());
    assertThat(emitPreviewRpcSemconv(openTelemetry)).isEqualTo(expected.version() >= 1);
    assertThat(SemconvStability.rpcSchemaUrl(openTelemetry))
        .isEqualTo(expected.version() >= 1 ? SchemaUrls.V1_44_0 : SchemaUrls.V1_37_0);
  }

  private static Stream<Arguments> structuredRpcSelections() {
    return Stream.of(
        Arguments.of(0, false, true, SemconvMode.V0_STABLE),
        Arguments.of(1, true, false, SemconvMode.V1_EXPERIMENTAL),
        Arguments.of(1, true, true, SemconvMode.V1_EXPERIMENTAL.withDualEmit()));
  }

  private static Stream<Arguments> previewSelections() {
    return Stream.of("rpc", "service.peer")
        .flatMap(
            domain ->
                Stream.of(
                    Arguments.of(domain, "", "", SemconvMode.V0_STABLE),
                    Arguments.of(domain, "", domain, SemconvMode.V1_EXPERIMENTAL),
                    Arguments.of(
                        domain, "", domain + "/dup", SemconvMode.V1_EXPERIMENTAL.withDualEmit()),
                    Arguments.of(domain, domain + "/dup", domain, SemconvMode.V1_EXPERIMENTAL),
                    Arguments.of(
                        domain,
                        domain,
                        domain + "/dup",
                        SemconvMode.V1_EXPERIMENTAL.withDualEmit())));
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

  private static Entry<String, Object> stabilityOptInList(String value) {
    return property("stability_opt_in_list", value);
  }

  private static Entry<String, Object> property(String name, Object value) {
    return new SimpleImmutableEntry<String, Object>(name, value);
  }

  @SafeVarargs
  private static Entry<String, Object> structured(String name, Entry<String, Object>... entries) {
    return property(name, general(entries));
  }

  private static Entry<String, Object> domainSemconv(String domain, int version) {
    return domainSemconv(domain, version, false);
  }

  private static Entry<String, Object> domainSemconv(String domain, int version, boolean dualEmit) {
    return domainSemconv(domain, version, false, dualEmit);
  }

  private static Entry<String, Object> domainSemconv(
      String domain, int version, boolean experimental, boolean dualEmit) {
    return structured(
        domain,
        structured(
            "semconv",
            property("version", version),
            property("experimental", experimental),
            property("dual_emit", dualEmit)));
  }

  private static Set<String> stableOptIn(String... values) {
    return new HashSet<String>(asList(values));
  }

  private static Set<String> preview(String... values) {
    return new HashSet<String>(asList(values));
  }

  private static Set<String> noStableOptIn() {
    return emptySet();
  }

  private static Set<String> noPreview() {
    return emptySet();
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
