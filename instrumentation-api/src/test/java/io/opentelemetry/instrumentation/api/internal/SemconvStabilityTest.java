/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.common.ComponentLoader;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SemconvStabilityTest {

  @Test
  void resolveGeneralStableFlags_parsesCommaSeparatedList() {
    // general:
    //   stability_opt_in_list: [database/dup, code, messaging]
    DeclarativeConfigProperties general =
        general(stabilityOptInList(" database/dup, code , messaging "));

    assertThat(SemconvSelectionResolver.resolveGeneralStableFlags(general))
        .containsExactlyInAnyOrder("database/dup", "code", "messaging");
  }

  @Test
  void parseCommaSeparatedSet_ignoresBlankEntries() {
    assertThat(SemconvSelectionResolver.parseCommaSeparatedSet("rpc, messaging, ,database/dup,"))
        .containsExactlyInAnyOrder("rpc", "messaging", "database/dup");
  }

  @Test
  void explicitDomainConfigTakesPrecedenceWhenV3PreviewIsDisabled() {
    // general:
    //   stability_opt_in_list: [database]
    //   db:
    //     semconv:
    //       version: 1
    //       dual_emit: true
    DeclarativeConfigProperties general =
        general(stabilityOptInList("database"), domainSemconv("db", 1, true));
    boolean v3Preview = false;

    // otel.semconv-stability.opt-in=database
    SemconvSelection database =
        new SemconvSelectionResolver(general, v3Preview, stableOptIn("database"), noPreview())
            .database();

    assertThat(database.emitOld()).isTrue();
    assertThat(database.emitStable()).isTrue();
  }

  @Test
  void v3PreviewForcesStableEvenWithExplicitDomainConfig() {
    // general:
    //   db:
    //     semconv:
    //       version: 0
    // java:
    //   common:
    //     v3_preview: true
    DeclarativeConfigProperties general = general(domainSemconv("db", 0));
    boolean v3Preview = true;

    SemconvSelection database =
        new SemconvSelectionResolver(general, v3Preview, noStableOptIn(), noPreview()).database();

    assertThat(database.emitOld()).isFalse();
    assertThat(database.emitStable()).isTrue();
  }

  @Test
  void explicitDomainVersionZeroMeansLegacyOnlyWhenV3PreviewIsDisabled() {
    // general:
    //   db:
    //     semconv:
    //       version: 0
    DeclarativeConfigProperties general = general(domainSemconv("db", 0));
    boolean v3Preview = false;

    SemconvSelection database =
        new SemconvSelectionResolver(general, v3Preview, noStableOptIn(), noPreview()).database();

    assertThat(database.emitOld()).isTrue();
    assertThat(database.emitStable()).isFalse();
  }

  @Test
  void stableOptInAppliesToDatabaseAndCode() {
    // general:
    //   stability_opt_in_list: [database, code]
    //
    // Same resolver input is also produced by bridged otel.semconv-stability.opt-in=database,code.
    DeclarativeConfigProperties general = general(stabilityOptInList("database, code"));
    boolean v3Preview = false;
    // otel.semconv-stability.opt-in=database,code
    Set<String> stableOptIn = stableOptIn("database", "code");

    SemconvSelectionResolver resolver =
        new SemconvSelectionResolver(general, v3Preview, stableOptIn, noPreview());
    SemconvSelection database = resolver.database();
    SemconvSelection code = resolver.code();

    assertThat(database.emitOld()).isFalse();
    assertThat(database.emitStable()).isTrue();
    assertThat(code.emitOld()).isFalse();
    assertThat(code.emitStable()).isTrue();
  }

  @Test
  void stableOptInDupDualEmitsDatabaseAndCodeWhenV3PreviewIsDisabled() {
    // general:
    //   stability_opt_in_list: [database/dup, code/dup]
    // java:
    //   common:
    //     v3_preview: false
    DeclarativeConfigProperties general = general(stabilityOptInList("database/dup, code/dup"));
    boolean v3Preview = false;
    // otel.semconv-stability.opt-in=database/dup,code/dup
    Set<String> stableOptIn = stableOptIn("database/dup", "code/dup");

    SemconvSelectionResolver resolver =
        new SemconvSelectionResolver(general, v3Preview, stableOptIn, noPreview());
    SemconvSelection database = resolver.database();
    SemconvSelection code = resolver.code();

    assertThat(database.emitOld()).isTrue();
    assertThat(database.emitStable()).isTrue();
    assertThat(code.emitOld()).isTrue();
    assertThat(code.emitStable()).isTrue();
  }

  @Test
  void stableOptInDupEmitsStableDatabaseAndCodeWhenV3PreviewIsEnabled() {
    // general:
    //   stability_opt_in_list: [database/dup, code/dup]
    // java:
    //   common:
    //     v3_preview: true
    DeclarativeConfigProperties general = general(stabilityOptInList("database/dup, code/dup"));
    boolean v3Preview = true;
    // otel.semconv-stability.opt-in=database/dup,code/dup
    Set<String> stableOptIn = stableOptIn("database/dup", "code/dup");

    SemconvSelectionResolver resolver =
        new SemconvSelectionResolver(general, v3Preview, stableOptIn, noPreview());
    SemconvSelection database = resolver.database();
    SemconvSelection code = resolver.code();

    assertThat(database.emitOld()).isFalse();
    assertThat(database.emitStable()).isTrue();
    assertThat(code.emitOld()).isFalse();
    assertThat(code.emitStable()).isTrue();
  }

  @Test
  void stableOptInAppliesToPreviewDomainsWhenV3PreviewIsDisabled() {
    // general:
    //   stability_opt_in_list: [rpc, service.peer, messaging]
    // java:
    //   common:
    //     v3_preview: false
    DeclarativeConfigProperties general =
        general(stabilityOptInList("rpc, service.peer, messaging"));
    boolean v3Preview = false;
    // otel.semconv-stability.opt-in=rpc,service.peer,messaging
    Set<String> stableOptIn = stableOptIn("rpc", "service.peer", "messaging");

    SemconvSelectionResolver resolver =
        new SemconvSelectionResolver(general, v3Preview, stableOptIn, noPreview());
    SemconvSelection rpc = resolver.rpc();
    SemconvSelection servicePeer = resolver.servicePeer();
    SemconvSelection messaging = resolver.messaging();

    assertThat(rpc.emitOld()).isFalse();
    assertThat(rpc.emitStable()).isTrue();
    assertThat(servicePeer.emitOld()).isFalse();
    assertThat(servicePeer.emitStable()).isTrue();
    assertThat(messaging.emitOld()).isFalse();
    assertThat(messaging.emitStable()).isTrue();
  }

  @Test
  void v3PreviewIgnoresStableOptInForPreviewDomains() {
    // general:
    //   stability_opt_in_list: [rpc, service.peer, messaging]
    // java:
    //   common:
    //     v3_preview: true
    DeclarativeConfigProperties general =
        general(stabilityOptInList("rpc, service.peer, messaging"));
    boolean v3Preview = true;
    // otel.semconv-stability.opt-in=rpc,service.peer,messaging
    Set<String> stableOptIn = stableOptIn("rpc", "service.peer", "messaging");

    SemconvSelectionResolver resolver =
        new SemconvSelectionResolver(general, v3Preview, stableOptIn, noPreview());
    SemconvSelection rpc = resolver.rpc();
    SemconvSelection servicePeer = resolver.servicePeer();
    SemconvSelection messaging = resolver.messaging();

    assertThat(rpc.emitOld()).isTrue();
    assertThat(rpc.emitStable()).isFalse();
    assertThat(servicePeer.emitOld()).isTrue();
    assertThat(servicePeer.emitStable()).isFalse();
    assertThat(messaging.emitOld()).isTrue();
    assertThat(messaging.emitStable()).isFalse();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void previewFallbackAppliesToPreviewDomains(boolean v3Preview) {
    // java:
    //   common:
    //     v3_preview: <v3Preview>
    //     semconv_stability:
    //       preview: [rpc, service.peer, messaging]
    DeclarativeConfigProperties general = general();
    // otel.semconv-stability.preview=rpc,service.peer,messaging
    Set<String> preview = preview("rpc", "service.peer", "messaging");

    SemconvSelectionResolver resolver =
        new SemconvSelectionResolver(general, v3Preview, noStableOptIn(), preview);
    SemconvSelection rpc = resolver.rpc();
    SemconvSelection servicePeer = resolver.servicePeer();
    SemconvSelection messaging = resolver.messaging();

    assertThat(rpc.emitOld()).isFalse();
    assertThat(rpc.emitStable()).isTrue();
    assertThat(servicePeer.emitOld()).isFalse();
    assertThat(servicePeer.emitStable()).isTrue();
    assertThat(messaging.emitOld()).isFalse();
    assertThat(messaging.emitStable()).isTrue();
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
    return structured(
        domain,
        structured("semconv", property("version", version), property("dual_emit", dualEmit)));
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
