/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.config.internal;

import static io.opentelemetry.instrumentation.api.incubator.config.internal.SelectorConfig.Stability.STABLE;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.api.internal.DeprecatedCaptureNames;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

@ResourceLock("SelectorConfig.logger")
class SelectorConfigTest {

  private static final String SELECTOR = "mdc-attributes";
  private static final String BOOLEAN_SELECTOR = "key-value-pair-attributes";
  private static final String RENAMED_SELECTOR = "logstash-structured-argument-attributes";
  private static final String DEPRECATED_RENAMED_SELECTOR = "logstash-structured-arguments";

  @Test
  void readsSelector() {
    DeclarativeConfigProperties config = mockConfig();
    DeclarativeConfigProperties selectorNode = config.get("mdc_attributes/development");
    when(selectorNode.getScalarList("included", String.class))
        .thenReturn(asList("exact", "prefix.*", "single?"));
    when(selectorNode.getScalarList("excluded", String.class))
        .thenReturn(singletonList("prefix.secret"));
    when(config.get("mdc_attributes").getScalarList("included", String.class))
        .thenReturn(singletonList("stable-only"));

    Predicate<String> selector = SelectorConfig.resolveLegacyLiteral(config, "test", SELECTOR);

    assertThat(selector).isNotNull();
    assertThat(selector.test("exact")).isTrue();
    assertThat(selector.test("prefix.value")).isTrue();
    assertThat(selector.test("single1")).isTrue();
    assertThat(selector.test("single22")).isFalse();
    assertThat(selector.test("prefix.secret")).isFalse();
    assertThat(selector.test("stable-only")).isFalse();
    assertThat(selector.test("other")).isFalse();
  }

  @Test
  void readsStableSelector() {
    DeclarativeConfigProperties config = mockStableConfig();
    DeclarativeConfigProperties selectorNode = config.get("mdc_attributes");
    when(selectorNode.getScalarList("included", String.class))
        .thenReturn(asList("exact", "prefix.*"));
    when(selectorNode.getScalarList("excluded", String.class))
        .thenReturn(singletonList("prefix.secret"));
    when(config.get("mdc_attributes/development").getScalarList("included", String.class))
        .thenReturn(singletonList("wrong"));

    IncludeExclude selector = SelectorConfig.resolve(config, "test", SELECTOR, STABLE);

    assertThat(selector).isNotNull();
    assertThat(selector.matches("exact")).isTrue();
    assertThat(selector.matches("prefix.value")).isTrue();
    assertThat(selector.matches("prefix.secret")).isFalse();
    assertThat(selector.matches("wrong")).isFalse();
  }

  @Test
  void stableExcludeOnlySelectorSelectsAllExceptExclusions() {
    DeclarativeConfigProperties config = mockStableConfig();
    when(config.get("mdc_attributes").getScalarList("excluded", String.class))
        .thenReturn(singletonList("secret*"));

    IncludeExclude selector = SelectorConfig.resolve(config, "test", SELECTOR, STABLE);

    assertThat(selector).isNotNull();
    assertThat(selector.matches("public")).isTrue();
    assertThat(selector.matches("secret-token")).isFalse();
  }

  @Test
  void excludeOnlySelectorSelectsAllExceptExclusions() {
    DeclarativeConfigProperties config = mockConfig();
    when(config.get("mdc_attributes/development").getScalarList("excluded", String.class))
        .thenReturn(singletonList("secret*"));

    Predicate<String> selector = SelectorConfig.resolveLegacyLiteral(config, "test", SELECTOR);

    assertThat(selector).isNotNull();
    assertThat(selector.test("public")).isTrue();
    assertThat(selector.test("secret-token")).isFalse();
  }

  @Test
  void selectorLoneWildcardCapturesAll() {
    DeclarativeConfigProperties config = mockConfig();
    when(config.get("mdc_attributes/development").getScalarList("included", String.class))
        .thenReturn(singletonList("*"));

    Predicate<String> selector = SelectorConfig.resolveLegacyLiteral(config, "test", SELECTOR);

    assertThat(selector).isNotNull();
    assertThat(selector.test("anything")).isTrue();
    assertThat(selector.test("literal?")).isTrue();
  }

  @Test
  void absentAndEmptySelectorsCaptureNothing() {
    DeclarativeConfigProperties absent = mockConfig();
    DeclarativeConfigProperties empty = mockConfig();
    when(empty.get("mdc_attributes/development").getScalarList("included", String.class))
        .thenReturn(emptyList());
    when(empty.get("mdc_attributes/development").getScalarList("excluded", String.class))
        .thenReturn(emptyList());

    assertThat(SelectorConfig.resolveLegacyLiteral(absent, "test", SELECTOR)).isNull();
    assertThat(SelectorConfig.resolveLegacyLiteral(empty, "test", SELECTOR)).isNull();
    assertThat(SelectorConfig.resolve(absent, "test", SELECTOR)).isNull();
    assertThat(SelectorConfig.resolve(empty, "test", SELECTOR)).isNull();
  }

  @Test
  void absentAndEmptyStableSelectorsCaptureNothing() {
    DeclarativeConfigProperties absent = mockStableConfig();
    DeclarativeConfigProperties empty = mockStableConfig();
    when(empty.get("mdc_attributes").getScalarList("included", String.class))
        .thenReturn(emptyList());
    when(empty.get("mdc_attributes").getScalarList("excluded", String.class))
        .thenReturn(emptyList());

    assertThat(SelectorConfig.resolve(absent, "stable-absent", SELECTOR, STABLE)).isNull();
    assertThat(SelectorConfig.resolve(empty, "stable-empty", SELECTOR, STABLE)).isNull();
  }

  @Test
  void deprecatedConfigUsesExactMatchingAndWarnsOnce() {
    DeclarativeConfigProperties config = mockConfig();
    when(config.getScalarList("capture_mdc_attributes/development", String.class))
        .thenReturn(asList("literal?", "embedded*", "*"));
    TestHandler handler = attachWarningHandler();
    try {
      Predicate<String> first =
          SelectorConfig.resolveLegacyLiteral(config, "exact-matching", SELECTOR);
      Predicate<String> second =
          SelectorConfig.resolveLegacyLiteral(config, "exact-matching", SELECTOR);

      assertThat(first).isNotNull();
      assertThat(first.test("literal?")).isTrue();
      assertThat(first.test("literal1")).isFalse();
      assertThat(first.test("embedded*")).isTrue();
      assertThat(first.test("embedded-value")).isFalse();
      assertThat(first.test("*")).isTrue();
      assertThat(first.test("other")).isFalse();
      assertThat(second).isNotNull();
      assertThat(second.test("other")).isFalse();
      assertThat(handler.records).hasSize(1);
      assertThat(handler.records.get(0).getMessage())
          .isEqualTo(
              "The otel.instrumentation.exact-matching.experimental.capture-mdc-attributes"
                  + " setting and the equivalent declarative configuration property are deprecated"
                  + " and may be removed in the next minor release. Use"
                  + " otel.instrumentation.exact-matching.experimental.mdc-attributes.included"
                  + " or otel.instrumentation.exact-matching.experimental.mdc-attributes.excluded"
                  + " or equivalent declarative configuration instead.");
    } finally {
      detachWarningHandler(handler);
    }
  }

  @Test
  void stableSelectorFallsBackToExperimentalDeprecatedConfig() {
    DeclarativeConfigProperties config = mockStableConfig();
    when(config.getScalarList("capture_mdc_attributes/development", String.class))
        .thenReturn(singletonList("legacy"));
    TestHandler handler = attachWarningHandler();
    try {
      IncludeExclude selector =
          SelectorConfig.resolve(config, "stable-deprecated", SELECTOR, STABLE);

      assertThat(selector).isNotNull();
      assertThat(selector.matches("legacy")).isTrue();
      assertThat(selector.matches("other")).isFalse();
      verify(config, never()).getScalarList("capture_mdc_attributes", String.class);
      assertThat(handler.records).hasSize(1);
      assertThat(handler.records.get(0).getMessage())
          .isEqualTo(
              "The otel.instrumentation.stable-deprecated.experimental.capture-mdc-attributes"
                  + " setting and the equivalent declarative configuration property are deprecated"
                  + " and may be removed in the next minor release. Use"
                  + " otel.instrumentation.stable-deprecated.mdc-attributes.included"
                  + " or otel.instrumentation.stable-deprecated.mdc-attributes.excluded"
                  + " or equivalent declarative configuration instead.");
    } finally {
      detachWarningHandler(handler);
    }
  }

  @Test
  void deprecatedConfigLoneWildcardCapturesAll() {
    DeclarativeConfigProperties config = mockConfig();
    when(config.getScalarList("capture_mdc_attributes/development", String.class))
        .thenReturn(singletonList("*"));

    Predicate<String> selector =
        SelectorConfig.resolveLegacyLiteral(config, "lone-wildcard", SELECTOR);

    assertThat(selector).isNotNull();
    assertThat(selector.test("literal?")).isTrue();
    assertThat(selector.test("embedded-value")).isTrue();
  }

  @Test
  void deprecatedEmptyListCapturesNothing() {
    DeclarativeConfigProperties config = mockConfig();
    when(config.getScalarList("capture_mdc_attributes/development", String.class))
        .thenReturn(emptyList());

    assertThat(SelectorConfig.resolveLegacyLiteral(config, "deprecated-empty", SELECTOR)).isNull();
    assertThat(SelectorConfig.resolve(config, "deprecated-empty-resolve", SELECTOR)).isNull();
  }

  @Test
  void selectorTakesPrecedenceWithoutWarning() {
    DeclarativeConfigProperties config = mockConfig();
    when(config.get("mdc_attributes/development").getScalarList("included", String.class))
        .thenReturn(singletonList("new"));
    when(config.getScalarList("capture_mdc_attributes/development", String.class))
        .thenReturn(singletonList("legacy"));
    TestHandler handler = attachWarningHandler();
    try {
      Predicate<String> first = SelectorConfig.resolveLegacyLiteral(config, "precedence", SELECTOR);
      Predicate<String> second =
          SelectorConfig.resolveLegacyLiteral(config, "precedence", SELECTOR);
      IncludeExclude resolved = SelectorConfig.resolve(config, "precedence", SELECTOR);

      assertThat(first).isNotNull();
      assertThat(first.test("new")).isTrue();
      assertThat(first.test("legacy")).isFalse();
      assertThat(second).isNotNull();
      assertThat(second.test("new")).isTrue();
      assertThat(resolved).isNotNull();
      assertThat(resolved.matches("new")).isTrue();
      assertThat(resolved.matches("legacy")).isFalse();
      assertThat(handler.records).isEmpty();
      verify(config, never()).getScalarList("capture_mdc_attributes/development", String.class);
    } finally {
      detachWarningHandler(handler);
    }
  }

  @Test
  void stableSelectorTakesPrecedenceOverExperimentalDeprecatedConfig() {
    DeclarativeConfigProperties config = mockStableConfig();
    when(config.get("mdc_attributes").getScalarList("included", String.class))
        .thenReturn(singletonList("new"));
    when(config.getScalarList("capture_mdc_attributes/development", String.class))
        .thenReturn(singletonList("legacy"));
    TestHandler handler = attachWarningHandler();
    try {
      IncludeExclude selector =
          SelectorConfig.resolve(config, "stable-precedence", SELECTOR, STABLE);

      assertThat(selector).isNotNull();
      assertThat(selector.matches("new")).isTrue();
      assertThat(selector.matches("legacy")).isFalse();
      assertThat(handler.records).isEmpty();
      verify(config, never()).getScalarList("capture_mdc_attributes/development", String.class);
    } finally {
      detachWarningHandler(handler);
    }
  }

  @Test
  @ResourceLock(Resources.SYSTEM_PROPERTIES)
  void stableSystemPropertyFallbackIsOnlyUsedWhenEnabled() {
    DeclarativeConfigProperties config = mockStableConfig();
    System.setProperty(
        "otel.instrumentation.stable-flat.mdc-attributes.included", "public*,secret*");
    System.setProperty("otel.instrumentation.stable-flat.mdc-attributes.excluded", "secret*");
    System.setProperty(
        "otel.instrumentation.stable-flat.experimental.mdc-attributes.included", "wrong");
    try {
      assertThat(SelectorConfig.resolve(config, "stable-flat", SELECTOR, STABLE)).isNull();

      IncludeExclude selector =
          SelectorConfig.resolve(config, "stable-flat", SELECTOR, STABLE, true);

      assertThat(selector).isNotNull();
      assertThat(selector.matches("public-value")).isTrue();
      assertThat(selector.matches("secret-value")).isFalse();
      assertThat(selector.matches("wrong")).isFalse();
    } finally {
      System.clearProperty("otel.instrumentation.stable-flat.mdc-attributes.included");
      System.clearProperty("otel.instrumentation.stable-flat.mdc-attributes.excluded");
      System.clearProperty("otel.instrumentation.stable-flat.experimental.mdc-attributes.included");
    }
  }

  @Test
  void resolveMatchesDeprecatedValuesExactlyAndIgnoresWildcards() {
    DeclarativeConfigProperties config = mockConfig();
    when(config.getScalarList("capture_mdc_attributes/development", String.class))
        .thenReturn(asList("exact.name", "prefix.*"));

    IncludeExclude selector = SelectorConfig.resolve(config, "resolve-deprecated", SELECTOR);

    assertThat(selector).isNotNull();
    assertThat(selector.matches("exact.name")).isTrue();
    assertThat(selector.matches("prefix.value")).isFalse();
    assertThat(selector.matches("other")).isFalse();
  }

  @Test
  void ignoredDeprecatedWildcardsPointToTheIncludedProperty() {
    DeclarativeConfigProperties config = mockStableConfig();
    when(config.getScalarList("capture_mdc_attributes/development", String.class))
        .thenReturn(asList("exact.name", "prefix.*"));
    TestHandler handler = new TestHandler();
    Logger logger = Logger.getLogger(DeprecatedCaptureNames.class.getName());
    logger.addHandler(handler);
    try {
      assertThat(SelectorConfig.resolve(config, "ignored-wildcards", SELECTOR, STABLE)).isNotNull();

      assertThat(handler.records).hasSize(1);
      assertThat(handler.records.get(0).getMessage())
          .isEqualTo(
              "Ignoring [prefix.*] configured in the"
                  + " otel.instrumentation.ignored-wildcards.experimental.capture-mdc-attributes"
                  + " setting or equivalent declarative configuration, which matches names"
                  + " literally and never supported wildcards. Use"
                  + " otel.instrumentation.ignored-wildcards.mdc-attributes.included or equivalent"
                  + " declarative configuration to match names by pattern.");
    } finally {
      logger.removeHandler(handler);
    }
  }

  @Test
  void resolveIgnoresDeprecatedValuesThatAreAllWildcards() {
    DeclarativeConfigProperties config = mockConfig();
    when(config.getScalarList("capture_mdc_attributes/development", String.class))
        .thenReturn(singletonList("*"));

    assertThat(SelectorConfig.resolve(config, "resolve-all-wildcards", SELECTOR)).isNull();
  }

  @Test
  void derivesNodeNameFromSelectorName() {
    DeclarativeConfigProperties config =
        mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
    when(config.get("request_parameters/development").getScalarList("included", String.class))
        .thenReturn(singletonList("id"));
    when(config.get("request_parameters/development").getScalarList("excluded", String.class))
        .thenReturn(null);
    when(config.getScalarList("capture_request_parameters/development", String.class))
        .thenReturn(null);

    IncludeExclude selector = SelectorConfig.resolve(config, "servlet", "request-parameters");

    assertThat(selector).isNotNull();
    assertThat(selector.matches("id")).isTrue();
    assertThat(selector.matches("other")).isFalse();
  }

  @Test
  void resolvesLegacyLiteralValuesDirectly() {
    assertThat(SelectorConfig.resolveLegacyLiteral(emptyList())).isNull();

    Predicate<String> all = SelectorConfig.resolveLegacyLiteral(singletonList("*"));
    assertThat(all).isNotNull();
    assertThat(all.test("anything")).isTrue();

    Predicate<String> literal = SelectorConfig.resolveLegacyLiteral(asList("embedded*", "*"));
    assertThat(literal).isNotNull();
    assertThat(literal.test("embedded*")).isTrue();
    assertThat(literal.test("embedded-value")).isFalse();
    assertThat(literal.test("*")).isTrue();
    assertThat(literal.test("other")).isFalse();
  }

  @Test
  void legacyBooleanSelectorTakesPrecedenceWithoutWarning() {
    DeclarativeConfigProperties config = mockBooleanConfig();
    when(config
            .get("key_value_pair_attributes/development")
            .getScalarList("included", String.class))
        .thenReturn(singletonList("new"));
    when(config.getBoolean("capture_key_value_pair_attributes/development")).thenReturn(true);
    TestHandler handler = attachWarningHandler();
    try {
      Predicate<String> selector =
          SelectorConfig.resolveLegacyBoolean(config, "boolean-precedence", BOOLEAN_SELECTOR);

      assertThat(selector).isNotNull();
      assertThat(selector.test("new")).isTrue();
      assertThat(selector.test("other")).isFalse();
      assertThat(handler.records).isEmpty();
      verify(config, never()).getBoolean("capture_key_value_pair_attributes/development");
    } finally {
      detachWarningHandler(handler);
    }
  }

  @Test
  void legacyBooleanTrueCapturesEverythingAndWarnsOnce() {
    DeclarativeConfigProperties config = mockBooleanConfig();
    when(config.getBoolean("capture_key_value_pair_attributes/development")).thenReturn(true);
    TestHandler handler = attachWarningHandler();
    try {
      Predicate<String> first =
          SelectorConfig.resolveLegacyBoolean(config, "boolean-enabled", BOOLEAN_SELECTOR);
      Predicate<String> second =
          SelectorConfig.resolveLegacyBoolean(config, "boolean-enabled", BOOLEAN_SELECTOR);

      assertThat(first).isNotNull();
      assertThat(first.test("anything")).isTrue();
      assertThat(second).isNotNull();
      assertThat(handler.records).hasSize(1);
      assertThat(handler.records.get(0).getMessage())
          .isEqualTo(
              "The otel.instrumentation.boolean-enabled.experimental"
                  + ".capture-key-value-pair-attributes setting and the equivalent declarative"
                  + " configuration property are deprecated and may be removed in the next minor"
                  + " release. Use otel.instrumentation.boolean-enabled.experimental"
                  + ".key-value-pair-attributes.included or otel.instrumentation.boolean-enabled"
                  + ".experimental.key-value-pair-attributes.excluded or equivalent declarative"
                  + " configuration instead.");
    } finally {
      detachWarningHandler(handler);
    }
  }

  @Test
  void legacyBooleanFalseCapturesNothing() {
    DeclarativeConfigProperties config = mockBooleanConfig();
    when(config.getBoolean("capture_key_value_pair_attributes/development")).thenReturn(false);

    assertThat(SelectorConfig.resolveLegacyBoolean(config, "boolean-disabled", BOOLEAN_SELECTOR))
        .isNull();
  }

  @Test
  void absentLegacyBooleanCapturesNothing() {
    DeclarativeConfigProperties config = mockBooleanConfig();
    TestHandler handler = attachWarningHandler();
    try {
      assertThat(SelectorConfig.resolveLegacyBoolean(config, "boolean-absent", BOOLEAN_SELECTOR))
          .isNull();
      assertThat(handler.records).isEmpty();
    } finally {
      detachWarningHandler(handler);
    }
  }

  @Test
  void renamedLegacyBooleanIsReadFromTheDeprecatedName() {
    DeclarativeConfigProperties config = mockRenamedBooleanConfig();
    when(config.getBoolean("capture_logstash_structured_arguments/development")).thenReturn(true);
    // deriving the deprecated name from the selector name would read this instead
    when(config.getBoolean("capture_logstash_structured_argument_attributes/development"))
        .thenReturn(false);
    TestHandler handler = attachWarningHandler();
    try {
      Predicate<String> selector =
          SelectorConfig.resolveLegacyBoolean(
              config, "boolean-renamed", RENAMED_SELECTOR, DEPRECATED_RENAMED_SELECTOR);

      assertThat(selector).isNotNull();
      assertThat(selector.test("anything")).isTrue();
      verify(config, never())
          .getBoolean("capture_logstash_structured_argument_attributes/development");
      assertThat(handler.records).hasSize(1);
      assertThat(handler.records.get(0).getMessage())
          .isEqualTo(
              "The otel.instrumentation.boolean-renamed.experimental"
                  + ".capture-logstash-structured-arguments setting and the equivalent declarative"
                  + " configuration property are deprecated and may be removed in the next minor"
                  + " release. Use otel.instrumentation.boolean-renamed.experimental"
                  + ".logstash-structured-argument-attributes.included or"
                  + " otel.instrumentation.boolean-renamed.experimental"
                  + ".logstash-structured-argument-attributes.excluded or equivalent declarative"
                  + " configuration instead.");
    } finally {
      detachWarningHandler(handler);
    }
  }

  @Test
  void renamedSelectorIsReadFromTheReplacementName() {
    DeclarativeConfigProperties config = mockRenamedBooleanConfig();
    when(config
            .get("logstash_structured_argument_attributes/development")
            .getScalarList("included", String.class))
        .thenReturn(singletonList("new"));
    // deriving the selector name from the deprecated name would read this instead
    when(config
            .get("logstash_structured_arguments/development")
            .getScalarList("included", String.class))
        .thenReturn(singletonList("wrong"));
    when(config.getBoolean("capture_logstash_structured_arguments/development")).thenReturn(true);

    Predicate<String> selector =
        SelectorConfig.resolveLegacyBoolean(
            config, "boolean-renamed-selector", RENAMED_SELECTOR, DEPRECATED_RENAMED_SELECTOR);

    assertThat(selector).isNotNull();
    assertThat(selector.test("new")).isTrue();
    assertThat(selector.test("wrong")).isFalse();
  }

  private static DeclarativeConfigProperties mockRenamedBooleanConfig() {
    DeclarativeConfigProperties config =
        mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
    DeclarativeConfigProperties selectorNode =
        config.get("logstash_structured_argument_attributes/development");
    when(selectorNode.getScalarList("included", String.class)).thenReturn(null);
    when(selectorNode.getScalarList("excluded", String.class)).thenReturn(null);
    when(config.getBoolean("capture_logstash_structured_arguments/development")).thenReturn(null);
    return config;
  }

  private static DeclarativeConfigProperties mockBooleanConfig() {
    DeclarativeConfigProperties config =
        mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
    DeclarativeConfigProperties selectorNode = config.get("key_value_pair_attributes/development");
    when(selectorNode.getScalarList("included", String.class)).thenReturn(null);
    when(selectorNode.getScalarList("excluded", String.class)).thenReturn(null);
    when(config.getBoolean("capture_key_value_pair_attributes/development")).thenReturn(null);
    return config;
  }

  private static DeclarativeConfigProperties mockConfig() {
    DeclarativeConfigProperties config =
        mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
    DeclarativeConfigProperties selectorNode = config.get("mdc_attributes/development");
    when(selectorNode.getScalarList("included", String.class)).thenReturn(null);
    when(selectorNode.getScalarList("excluded", String.class)).thenReturn(null);
    when(config.getScalarList("capture_mdc_attributes/development", String.class)).thenReturn(null);
    return config;
  }

  private static DeclarativeConfigProperties mockStableConfig() {
    DeclarativeConfigProperties config =
        mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
    DeclarativeConfigProperties selectorNode = config.get("mdc_attributes");
    when(selectorNode.getScalarList("included", String.class)).thenReturn(null);
    when(selectorNode.getScalarList("excluded", String.class)).thenReturn(null);
    when(config.getScalarList("capture_mdc_attributes/development", String.class)).thenReturn(null);
    return config;
  }

  private static TestHandler attachWarningHandler() {
    TestHandler handler = new TestHandler();
    Logger.getLogger(SelectorConfig.class.getName()).addHandler(handler);
    return handler;
  }

  private static void detachWarningHandler(TestHandler handler) {
    Logger.getLogger(SelectorConfig.class.getName()).removeHandler(handler);
  }

  private static final class TestHandler extends Handler {

    private final List<LogRecord> records = new ArrayList<>();

    @Override
    public void publish(LogRecord record) {
      records.add(record);
    }

    @Override
    public void flush() {}

    @Override
    public void close() {}
  }
}
