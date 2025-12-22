/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.servlet;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.internal.ConfigPropertiesUtil;

public class ExperimentalSnippetHolder {

  private static volatile String snippet = getSnippetSetting();

  private static String getSnippetSetting() {
    OpenTelemetry openTelemetry = GlobalOpenTelemetry.get();
    return DeclarativeConfigUtil.getInstrumentationConfig(openTelemetry, "servlet")
        .getString(
            "javascript_snippet/development",
            // use ConfigPropertiesUtil only to prevent that the deprecated property is used in
            // declarative config
            ConfigPropertiesUtil.getString("otel.experimental.javascript-snippet", ""));
  }

  public static void setSnippet(String newValue) {
    snippet = newValue;
  }

  public static String getSnippet() {
    return snippet;
  }

  private ExperimentalSnippetHolder() {}
}
