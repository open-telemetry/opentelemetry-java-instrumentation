/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.servlet;

import io.opentelemetry.instrumentation.api.internal.ConfigPropertiesUtil;
import java.util.concurrent.atomic.AtomicReference;

public class ExperimentalSnippetHolder {

  private static final AtomicReference<String> snippet = new AtomicReference<>(getSnippetSetting());

  private static String getSnippetSetting() {
    String result = ConfigPropertiesUtil.getString("otel.experimental.javascript-snippet");
    if (result == null) {
      return "";
    } else {
      return result;
    }
  }

  public static void setSnippet(String newValue) {
    snippet.compareAndSet("", newValue);
  }

  public static String getSnippet() {
    return snippet.get();
  }

  private ExperimentalSnippetHolder() {}
}
