/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.servlet;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.internal.ConfigPropertiesUtil;

public class ExperimentalSnippetHolder {

  private static volatile String snippet = getSnippetSetting();

  private static String getSnippetSetting() {
    OpenTelemetry openTelemetry = GlobalOpenTelemetry.get();
    String result =
        // otel.experimental.* does not fit the usual pattern of configuration properties for
        // instrumentations, so we need to handle both declarative and non-declarative configs here
        ConfigPropertiesUtil.isDeclarativeConfig(openTelemetry)
            ? ConfigPropertiesUtil.getString(
                openTelemetry, null, "servlet", "experimental", "javascript-snippet")
            : ConfigPropertiesUtil.getString("otel.experimental.javascript-snippet");
    return result == null ? "" : result;
  }

  public static void setSnippet(String newValue) {
    snippet = newValue;
  }

  public static String getSnippet() {
    return snippet;
  }

  private ExperimentalSnippetHolder() {}
}
