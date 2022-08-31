/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.servlet;

import java.io.UnsupportedEncodingException;

public class ExperimentalSnippetHolder {

  private static String snippet = "";

  public static void setSnippet(String snippet) {
    ExperimentalSnippetHolder.snippet = snippet;
  }

  public static String getSnippet() {
    return snippet;
  }

  public static byte[] getSnippetBytes(String encoding) throws UnsupportedEncodingException {
    return snippet.getBytes(encoding);
  }

  private ExperimentalSnippetHolder() {}
}
