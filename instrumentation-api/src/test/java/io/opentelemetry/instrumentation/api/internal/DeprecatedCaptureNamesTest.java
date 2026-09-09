/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.logging.Level.WARNING;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

class DeprecatedCaptureNamesTest {

  @Test
  void keepsNamesWithoutMetacharacters() {
    IncludeExclude selector = toSelector(asList("Content-Type", "X-Test"));

    assertThat(selector).isNotNull();
    assertThat(selector.matches("Content-Type")).isTrue();
    assertThat(selector.matches("X-Test")).isTrue();
    assertThat(selector.matches("Authorization")).isFalse();
  }

  @Test
  void dropsNamesContainingWildcards() {
    IncludeExclude selector = toSelector(asList("Content-Type", "X-*", "X-Tes?"));

    assertThat(selector).isNotNull();
    assertThat(selector.matches("Content-Type")).isTrue();
    assertThat(selector.matches("X-Test")).isFalse();
    assertThat(selector.matches("X-Other")).isFalse();
  }

  @Test
  void wildcardSelectsNothingRatherThanEveryName() {
    // "*" is a legal HTTP header name character, so this setting previously looked for a name
    // literally called "*", which matched nothing. It is dropped rather than reinterpreted as a
    // pattern that would match every name.
    assertThat(toSelector(singletonList("*"))).isNull();
    // an empty selector matches every name, so consumers treat it as "nothing configured"
    assertThat(toSelectorOrEmpty(singletonList("*")).isEmpty()).isTrue();
  }

  @Test
  void absentNamesProduceNoSelector() {
    assertThat(toSelector(null)).isNull();
    assertThat(toSelector(emptyList())).isNull();
    assertThat(toSelectorOrEmpty(null).isEmpty()).isTrue();
    assertThat(toSelectorOrEmpty(emptyList()).isEmpty()).isTrue();
  }

  @Test
  void warnsOnceNamingTheDroppedNamesAndTheReplacement() {
    List<LogRecord> records = new ArrayList<>();
    Logger logger = Logger.getLogger(DeprecatedCaptureNames.class.getName());
    Handler handler = new CollectingHandler(records);
    logger.addHandler(handler);
    try {
      String source = "warnsOnce.setting";
      DeprecatedCaptureNames.toSelector(asList("X-Test", "X-*"), source, "warnsOnce.included");
      DeprecatedCaptureNames.toSelector(singletonList("X-?"), source, "warnsOnce.included");

      assertThat(records).hasSize(1);
      assertThat(records.get(0).getLevel()).isEqualTo(WARNING);
      assertThat(records.get(0).getMessage())
          .contains("X-*")
          .doesNotContain("X-Test")
          .contains(source)
          .contains("warnsOnce.included");
    } finally {
      logger.removeHandler(handler);
    }
  }

  @Test
  void doesNotWarnWhenNoNameIsDropped() {
    List<LogRecord> records = new ArrayList<>();
    Logger logger = Logger.getLogger(DeprecatedCaptureNames.class.getName());
    Handler handler = new CollectingHandler(records);
    logger.addHandler(handler);
    try {
      DeprecatedCaptureNames.toSelector(
          singletonList("X-Test"), "doesNotWarn.setting", "doesNotWarn.included");

      assertThat(records).isEmpty();
    } finally {
      logger.removeHandler(handler);
    }
  }

  private static IncludeExclude toSelector(Collection<String> names) {
    return DeprecatedCaptureNames.toSelector(names, "toSelector.setting", "toSelector.included");
  }

  private static IncludeExclude toSelectorOrEmpty(Collection<String> names) {
    return DeprecatedCaptureNames.toSelectorOrEmpty(
        names, "toSelectorOrEmpty.setting", "toSelectorOrEmpty.included");
  }

  private static final class CollectingHandler extends Handler {

    private final List<LogRecord> records;

    CollectingHandler(List<LogRecord> records) {
      this.records = records;
    }

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
