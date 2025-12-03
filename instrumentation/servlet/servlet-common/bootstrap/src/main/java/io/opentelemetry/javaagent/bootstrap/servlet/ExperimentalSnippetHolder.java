/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.servlet;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.internal.ConfigPropertiesUtil;

public class ExperimentalSnippetHolder {

  private static volatile String snippet = getSnippetSetting();

  private static String getSnippetSetting() {
    return ConfigPropertiesUtil.getString(
            GlobalOpenTelemetry.get(), "servlet", "javascript-snippet/development")
        .orElse("");
  }

  public static void setSnippet(String newValue) {
    snippet = newValue;
  }

  public static String getSnippet() {
    return snippet;
  }

  private ExperimentalSnippetHolder() {}
}
