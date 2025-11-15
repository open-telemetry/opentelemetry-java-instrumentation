/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.instrumentation.http;

import static java.util.logging.Level.FINE;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Pattern;

final class UrlTemplateRules {
  private static final Logger logger = Logger.getLogger(UrlTemplateRules.class.getName());
  private static final List<Rule> rules = new ArrayList<>();

  static List<Rule> getRules() {
    return rules;
  }

  static void addRule(Pattern pattern, String replacement, boolean override) {
    logger.log(
        FINE,
        "Adding http client url template customization rule: pattern=\"{0}\", replacement=\"{1}\", override={2}.",
        new Object[] {pattern, replacement, override});

    rules.add(new Rule(pattern, replacement, override));
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
