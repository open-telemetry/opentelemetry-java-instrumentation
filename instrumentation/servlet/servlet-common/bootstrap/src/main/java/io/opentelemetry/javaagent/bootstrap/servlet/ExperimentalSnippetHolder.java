/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.servlet;

import static java.util.logging.Level.WARNING;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.incubator.config.InstrumentationConfigUtil;
import io.opentelemetry.instrumentation.api.internal.ConfigPropertiesUtil;
import io.opentelemetry.instrumentation.api.internal.ConfigProviderUtil;
import java.util.Optional;
import java.util.logging.Logger;

public class ExperimentalSnippetHolder {

  private static final Logger logger = Logger.getLogger(ExperimentalSnippetHolder.class.getName());

  private static final String DEPRECATED_PROPERTY_NAME = "otel.experimental.javascript-snippet";
  private static final String NEW_PROPERTY_NAME =
      "otel.instrumentation.servlet.experimental.javascript-snippet";

  private static volatile String snippet = getSnippetSetting();

  private static String getSnippetSetting() {
    Optional<String> snippet =
        Optional.ofNullable(
            InstrumentationConfigUtil.getOrNull(
                ConfigProviderUtil.getConfigProvider(GlobalOpenTelemetry.get()),
                config -> config.getString("javascript-snippet/development"),
                "java",
                "servlet"));
    // Can remove deprecated fallback in 2.24.0
    return snippet.orElseGet(
        () -> {
          String deprecatedValue = ConfigPropertiesUtil.getString(DEPRECATED_PROPERTY_NAME);
          if (deprecatedValue != null) {
            logger.log(
                WARNING,
                "Deprecated property \"{0}\" was used; use the \"{1}\" property instead",
                new Object[] {DEPRECATED_PROPERTY_NAME, NEW_PROPERTY_NAME});
            return deprecatedValue;
          }
          return "";
        });
  }

  public static void setSnippet(String newValue) {
    snippet = newValue;
  }

  public static String getSnippet() {
    return snippet;
  }

  private ExperimentalSnippetHolder() {}
}
