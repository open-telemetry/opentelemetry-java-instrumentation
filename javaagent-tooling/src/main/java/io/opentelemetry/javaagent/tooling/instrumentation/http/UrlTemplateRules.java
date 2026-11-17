/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.instrumentation.http;

import static java.util.logging.Level.FINE;

import java.util.Arrays;
import java.util.logging.Logger;
import java.util.regex.Pattern;

final class UrlTemplateRules {
  private static final Logger logger = Logger.getLogger(UrlTemplateRules.class.getName());
  private static volatile Rule[] rules = new Rule[0];

  static Rule[] getRules() {
    return rules;
  }

  static void addRule(Pattern pattern, String replacement, boolean override) {
    logger.log(
        FINE,
        "Adding http client url template customization rule: pattern=\"{0}\", replacement=\"{1}\", override={2}.",
        new Object[] {pattern, replacement, override});

    Rule[] current = rules;
    Rule[] updated = Arrays.copyOf(current, current.length + 1);
    updated[updated.length - 1] = new Rule(pattern, replacement, override);
    rules = updated;
  }

  static final class Rule {
    private final Pattern pattern;
    private final String replacement;
    private final boolean override;

    Rule(Pattern pattern, String replacement, boolean override) {
      this.pattern = pattern;
      this.replacement = replacement;
      this.override = override;
    }

    Pattern getPattern() {
      return pattern;
    }

    String getReplacement() {
      return replacement;
    }

    boolean getOverride() {
      return override;
    }
  }

  private UrlTemplateRules() {}
}
