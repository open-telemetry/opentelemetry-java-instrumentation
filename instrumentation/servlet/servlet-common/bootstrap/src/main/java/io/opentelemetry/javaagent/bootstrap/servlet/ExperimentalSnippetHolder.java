/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.servlet;

import java.util.concurrent.atomic.AtomicReference;

public class ExperimentalSnippetHolder {

  private static final AtomicReference<String> snippet = new AtomicReference<>("");

  public static void setSnippet(String newValue) {
    snippet.compareAndSet("", newValue);
    System.out.println("setSnippet to " + getSnippet());
  }

  public static String getSnippet() {
    return snippet.get();
  }

  private ExperimentalSnippetHolder() {}
}
