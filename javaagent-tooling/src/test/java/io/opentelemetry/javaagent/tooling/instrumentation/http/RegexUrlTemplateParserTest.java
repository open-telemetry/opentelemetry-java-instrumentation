/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.instrumentation.http;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RegexUrlTemplateParserTest {

  @AfterEach
  void reset() {
    UrlTemplateRules.getRules().clear();
  }

  @ParameterizedTest
  @CsvSource(
      value = {
        "a", "a,", "a;b", "a,;b", "(a,b",
      },
      delimiter = '|')
  void invalid(String rules) {
    RegexUrlTemplateCustomizerInitializer.parse(rules);
    assertThat(UrlTemplateRules.getRules()).isEmpty();
  }

  @Test
  void parse() {
    RegexUrlTemplateCustomizerInitializer.parse(
        "pattern1,replacement1;"
            + "pattern2,replacement2,false;"
            + "pattern3,replacement3,true;"
            + " pattern4 , replacement4 , true ;");
    assertThat(UrlTemplateRules.getRules())
        .satisfiesExactly(
            rule -> {
              assertThat(rule.getPattern().pattern()).isEqualTo("^pattern1$");
              assertThat(rule.getReplacement()).isEqualTo("replacement1");
              assertThat(rule.getOverride()).isFalse();
            },
            rule -> {
              assertThat(rule.getPattern().pattern()).isEqualTo("^pattern2$");
              assertThat(rule.getReplacement()).isEqualTo("replacement2");
              assertThat(rule.getOverride()).isFalse();
            },
            rule -> {
              assertThat(rule.getPattern().pattern()).isEqualTo("^pattern3$");
              assertThat(rule.getReplacement()).isEqualTo("replacement3");
              assertThat(rule.getOverride()).isTrue();
            },
            rule -> {
              assertThat(rule.getPattern().pattern()).isEqualTo("^pattern4$");
              assertThat(rule.getReplacement()).isEqualTo("replacement4");
              assertThat(rule.getOverride()).isTrue();
            });
  }
}
