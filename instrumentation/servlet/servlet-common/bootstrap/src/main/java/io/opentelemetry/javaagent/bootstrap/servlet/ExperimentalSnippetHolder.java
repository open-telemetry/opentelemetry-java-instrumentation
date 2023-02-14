/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.servlet;

public class ExperimentalSnippetHolder {

  private static String snippet = "";

  public static void setSnippet(String snippet) {
    ExperimentalSnippetHolder.snippet = snippet;
  }

  public static String getSnippet() {
    return snippet;
  }

  private ExperimentalSnippetHolder() {}
}
