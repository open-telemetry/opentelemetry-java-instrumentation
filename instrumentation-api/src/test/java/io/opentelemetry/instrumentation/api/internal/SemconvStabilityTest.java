/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.common.ComponentLoader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;

class SemconvStabilityTest {

  @Test
  void resolveStabilityOptInValues_parsesCommaSeparatedList() {
    DeclarativeConfigProperties config =
        new TestDeclarativeConfigProperties(
            mapOf("stability_opt_in_list", " database/dup, code , messaging "));

    assertThat(SemconvStability.resolveStabilityOptInValues(config))
        .containsExactlyInAnyOrder("database/dup", "code", "messaging");
  }

  @Test
  void parseCommaSeparatedSet_ignoresBlankEntries() {
    assertThat(SemconvStability.parseCommaSeparatedSet("rpc, messaging, ,database/dup,"))
        .containsExactlyInAnyOrder("rpc", "messaging", "database/dup");
  }

  @Test
  void domainSemconvSelection_takesPrecedenceOverStabilityList() {
    DeclarativeConfigProperties config =
        new TestDeclarativeConfigProperties(
            mapOf(
                "stability_opt_in_list",
                "database",
                "db",
                mapOf("semconv", mapOf("version", Integer.valueOf(1), "dual_emit", true))));

    SemconvStability.SemconvSelection selection =
        SemconvStability.resolveSemconvSelection(
            config, "db", "database", false, singleton("database"), emptySet());

    assertThat(selection.emitOld()).isTrue();
    assertThat(selection.emitStable()).isTrue();
  }

  @Test
  void domainSemconvSelection_versionZeroMeansLegacyOnly() {
    DeclarativeConfigProperties config =
        new TestDeclarativeConfigProperties(
            mapOf("db", mapOf("semconv", mapOf("version", Integer.valueOf(0)))));

    SemconvStability.SemconvSelection selection =
        SemconvStability.resolveDomainSemconvSelection(config, "db");

    assertThat(selection).isNotNull();
    assertThat(selection.emitOld()).isTrue();
    assertThat(selection.emitStable()).isFalse();
  }

  @Test
  void legacyFallbackIsUsedWhenStabilityListIsEmpty() {
    DeclarativeConfigProperties config = new TestDeclarativeConfigProperties(mapOf());

    SemconvStability.SemconvSelection selection =
        SemconvStability.resolveSemconvSelection(
            config, "db", "database", false, emptySet(), singleton("database"));

    assertThat(selection.emitOld()).isFalse();
    assertThat(selection.emitStable()).isTrue();
  }

  @Test
  void stabilityOptInListAppliesToRpcWhenDomainConfigIsAbsent() {
    DeclarativeConfigProperties config =
        new TestDeclarativeConfigProperties(mapOf("stability_opt_in_list", "rpc"));

    SemconvStability.SemconvSelection selection =
        SemconvStability.resolveSemconvSelection(
            config, "rpc", "rpc", false, singleton("rpc"), emptySet());

    assertThat(selection.emitOld()).isFalse();
    assertThat(selection.emitStable()).isTrue();
  }

  @Test
  void stabilityOptInListAppliesToMessagingWhenDomainConfigIsAbsent() {
    DeclarativeConfigProperties config =
        new TestDeclarativeConfigProperties(mapOf("stability_opt_in_list", "messaging"));

    SemconvStability.SemconvSelection selection =
        SemconvStability.resolveSemconvSelection(
            config, "messaging", "messaging", false, singleton("messaging"), emptySet());

    assertThat(selection.emitOld()).isFalse();
    assertThat(selection.emitStable()).isTrue();
  }

  private static Map<String, Object> mapOf(Object... values) {
    Map<String, Object> result = new HashMap<String, Object>();
    for (int i = 0; i < values.length; i += 2) {
      result.put((String) values[i], values[i + 1]);
    }
    return result;
  }

  private static final class TestDeclarativeConfigProperties
      implements DeclarativeConfigProperties {

    private final Map<String, Object> values;

    private TestDeclarativeConfigProperties(Map<String, Object> values) {
      this.values = values;
    }

    @Override
    @Nullable
    public String getString(String name) {
      Object value = values.get(name);
      return value instanceof String ? (String) value : null;
    }

    @Override
    @Nullable
    public Boolean getBoolean(String name) {
      Object value = values.get(name);
      return value instanceof Boolean ? (Boolean) value : null;
    }

    @Override
    @Nullable
    public Integer getInt(String name) {
      Object value = values.get(name);
      return value instanceof Integer ? (Integer) value : null;
    }

    @Override
    @Nullable
    public Long getLong(String name) {
      Object value = values.get(name);
      return value instanceof Long ? (Long) value : null;
    }

    @Override
    @Nullable
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
