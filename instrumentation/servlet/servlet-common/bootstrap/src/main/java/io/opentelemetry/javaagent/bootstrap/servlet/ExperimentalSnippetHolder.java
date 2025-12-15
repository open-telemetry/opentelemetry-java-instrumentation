/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.servlet;

import static io.opentelemetry.api.incubator.config.DeclarativeConfigProperties.empty;
import static java.util.logging.Level.WARNING;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.internal.ConfigPropertiesUtil;
import java.util.logging.Logger;

public class ExperimentalSnippetHolder {

  private static final Logger logger = Logger.getLogger(ExperimentalSnippetHolder.class.getName());

  private static final String DEPRECATED_PROPERTY_NAME = "otel.experimental.javascript-snippet";
  private static final String NEW_PROPERTY_NAME =
      "otel.instrumentation.servlet.experimental.javascript-snippet";

  private static volatile String snippet = getSnippetSetting();

  private static String getSnippetSetting() {
    String snippet =
        DeclarativeConfigUtil.getStructured(GlobalOpenTelemetry.get(), "java", empty())
            .getStructured("servlet", empty())
            .getString("javascript-snippet/development", null);
    // Can remove deprecated fallback in 2.24.0
    if (snippet != null) {
      return snippet;
    }
    String deprecatedValue = ConfigPropertiesUtil.getString(DEPRECATED_PROPERTY_NAME);
    if (deprecatedValue != null) {
      logger.log(
          WARNING,
          "Deprecated property \"{0}\" was used; use the \"{1}\" property instead",
          new Object[] {DEPRECATED_PROPERTY_NAME, NEW_PROPERTY_NAME});
      return deprecatedValue;
    }
    return "";
  }

  public static void setSnippet(String newValue) {
    snippet = newValue;
  }

  public static String getSnippet() {
    return snippet;
  }

  private ExperimentalSnippetHolder() {}
}
