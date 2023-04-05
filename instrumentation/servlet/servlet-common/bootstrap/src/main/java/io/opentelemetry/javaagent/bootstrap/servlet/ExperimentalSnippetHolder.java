/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.servlet;

public class ExperimentalSnippetHolder {

  private static volatile String snippet = "";

  private static boolean isSet = false;

  public static void setSnippet(String snippet) {
    if (isSet) {
      return;
    }
    ExperimentalSnippetHolder.snippet = snippet;
    isSet = true;
  }

  public static String getSnippet() {
    return snippet;
  }

  private ExperimentalSnippetHolder() {}
}
