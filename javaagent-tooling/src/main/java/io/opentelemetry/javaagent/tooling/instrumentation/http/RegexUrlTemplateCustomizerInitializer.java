/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.instrumentation.http;

import static io.opentelemetry.api.incubator.config.DeclarativeConfigProperties.empty;
import static java.util.Collections.emptyList;
import static java.util.logging.Level.WARNING;

import com.google.auto.service.AutoService;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
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
    DeclarativeConfigProperties instrumentationConfig =
        ((ExtendedOpenTelemetry) GlobalOpenTelemetry.get())
            .getConfigProvider()
            .getInstrumentationConfig();
    DeclarativeConfigProperties configuration =
        instrumentationConfig != null
            ? instrumentationConfig.getStructured("http", empty()).getStructured("client", empty())
            : null;
    // url template is emitted only when http client experimental telemetry is enabled
    if (configuration == null
        || !configuration.getBoolean("emit-experimental-telemetry/development", false)) {
      return;
    }
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
