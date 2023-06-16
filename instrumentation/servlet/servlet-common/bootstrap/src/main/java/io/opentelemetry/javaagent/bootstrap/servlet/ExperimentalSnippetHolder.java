/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.servlet;

import io.opentelemetry.instrumentation.api.internal.ConfigPropertiesUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ExperimentalSnippetHolder {

  private static volatile String snippet = getSnippetSetting();

  private static volatile List<String> headTagList = getHeadTagSetting();

  private static String getSnippetSetting() {
    String result = ConfigPropertiesUtil.getString("otel.experimental.javascript-snippet");
    return result == null ? "" : result;
  }

  private static List<String> getHeadTagSetting() {
    String headTagString = ConfigPropertiesUtil.getString("otel.experimental.head.tags");
    if (headTagString != null) {
      List<String> headTags = new ArrayList<>(Arrays.asList(headTagString.split(",")));
      if (!headTags.contains("<head>")) {
        headTags.add("<head>");
      }
      return headTags;
    }
    return Collections.singletonList("<head>");
  }

  public static List<String> getHeadTagList() {
    return headTagList;
  }

  public static void setSnippet(String newValue) {
    snippet = newValue;
  }

  public static String getSnippet() {
    return snippet;
  }

  private ExperimentalSnippetHolder() {}
}
