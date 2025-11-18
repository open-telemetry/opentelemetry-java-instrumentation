/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.instrumentation.http;

import static io.opentelemetry.api.incubator.config.DeclarativeConfigProperties.empty;
import static java.util.Collections.emptyList;
import static java.util.logging.Level.WARNING;

import com.google.auto.service.AutoService;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.incubator.config.internal.InstrumentationConfig;
import io.opentelemetry.javaagent.bootstrap.internal.AgentInstrumentationConfig;
import io.opentelemetry.javaagent.tooling.BeforeAgentListener;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@AutoService(BeforeAgentListener.class)
public final class RegexUrlTemplateCustomizerInitializer implements BeforeAgentListener {
  private static final Logger logger =
      Logger.getLogger(RegexUrlTemplateCustomizerInitializer.class.getName());

  @Override
  public void beforeAgent(AutoConfiguredOpenTelemetrySdk autoConfiguredOpenTelemetrySdk) {
    InstrumentationConfig config = AgentInstrumentationConfig.get();
    // url template is emitted only when http client experimental telemetry is enabled
    boolean urlTemplateEnabled =
        config.getBoolean("otel.instrumentation.http.client.emit-experimental-telemetry", false);
    if (!urlTemplateEnabled || !config.isDeclarative()) {
      return;
    }
    DeclarativeConfigProperties configuration =
        config.getDeclarativeConfig("http").getStructured("client", empty());
    configuration
        .getStructuredList("url_template_rules", emptyList())
        .forEach(
            rule -> {
              String patternString = rule.getString("pattern", "");
              String template = rule.getString("template", "");
              if (patternString.isEmpty() || template.isEmpty()) {
                return;
              }
              boolean override = rule.getBoolean("override", false);
              Pattern pattern = toPattern(patternString);
              if (pattern != null) {
                UrlTemplateRules.addRule(pattern, template, override);
              }
            });
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

      Pattern pattern = toPattern(parts[0].trim());
      if (pattern == null) {
        continue;
      }
      UrlTemplateRules.addRule(
          pattern, parts[1].trim(), parts.length == 3 && Boolean.parseBoolean(parts[2].trim()));
    }
  }

  private static Pattern toPattern(String patternString) {
    try {
      // ensure that pattern matches the whole url
      if (!patternString.startsWith("^")) {
        patternString = "^" + patternString;
      }
      if (!patternString.endsWith("$")) {
        patternString = patternString + "$";
      }
      return Pattern.compile(patternString);
    } catch (PatternSyntaxException exception) {
      logger.log(
          WARNING,
          "Invalid pattern in http client url template customization rule \""
              + patternString
              + "\".",
          exception);
      return null;
    }
  }
}
