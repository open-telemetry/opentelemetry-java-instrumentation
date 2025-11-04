/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.instrumentation.http;

import static java.util.logging.Level.WARNING;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.tooling.BeforeAgentListener;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.internal.AutoConfigureUtil;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@AutoService(BeforeAgentListener.class)
public final class RegexUrlTemplateCustomizerInitializer implements BeforeAgentListener {
  private static final Logger logger =
      Logger.getLogger(RegexUrlTemplateCustomizerInitializer.class.getName());

  @Override
  public void beforeAgent(AutoConfiguredOpenTelemetrySdk autoConfiguredOpenTelemetrySdk) {
    ConfigProperties config = AutoConfigureUtil.getConfig(autoConfiguredOpenTelemetrySdk);
    if (config == null) {
      return;
    }
    // url template is emitted only when http client experimental telemetry is enabled
    boolean urlTemplateEnabled =
        config.getBoolean("otel.instrumentation.http.client.emit-experimental-telemetry", false);
    if (!urlTemplateEnabled) {
      return;
    }
    String rules = config.getString("otel.instrumentation.http-client-url-template-rules");
    if (rules != null && !rules.isEmpty()) {
      parse(rules);
    }
  }

  // visible for testing
  static void parse(String rules) {
    // We are expecting a semicolon-separated list of rules in the form
    // pattern,replacement[,override]
    // Where pattern is a regex, replacement is the url template to use when the pattern matches,
    // override is an optional boolean (default false) indicating whether this rule should override
    // an existing url template. The pattern should match the entire url.
    for (String rule : rules.split(";")) {
      String[] parts = rule.split(",");
      if (parts.length != 2 && parts.length != 3) {
        logger.log(
            WARNING, "Invalid http client url template customization rule \"" + rule + "\".");
        continue;
      }

      Pattern pattern;
      try {
        String patternString = parts[0].trim();
        // ensure that pattern matches the whole url
        if (!patternString.startsWith("^")) {
          patternString = "^" + patternString;
        }
        if (!patternString.endsWith("$")) {
          patternString = patternString + "$";
        }
        pattern = Pattern.compile(patternString);
      } catch (PatternSyntaxException exception) {
        logger.log(
            WARNING,
            "Invalid pattern in http client url template customization rule \""
                + parts[0].trim()
                + "\".",
            exception);
        continue;
      }
      UrlTemplateRules.addRule(
          pattern, parts[1].trim(), parts.length == 3 && Boolean.parseBoolean(parts[2].trim()));
    }
  }
}
