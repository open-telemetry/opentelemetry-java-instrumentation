/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.servlet;

import java.util.concurrent.atomic.AtomicReference;

public class ExperimentalSnippetHolder {

  private static final AtomicReference<String> snippet = new AtomicReference<>("");

  private static boolean isSet = false;

  public static void setSnippet(String newValue) {
    String oldValue = snippet.get();
    while (!isSet) {
      isSet = snippet.compareAndSet(oldValue, newValue);
      oldValue = snippet.get();
    }
  }

  public static String getSnippet() {
    return snippet.get();
  }

  private ExperimentalSnippetHolder() {}
}
