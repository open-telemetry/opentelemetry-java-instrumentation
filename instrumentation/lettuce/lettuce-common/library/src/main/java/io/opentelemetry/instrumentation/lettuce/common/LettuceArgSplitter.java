/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.common;

import static java.util.Collections.emptyList;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

public final class LettuceArgSplitter {
  private static final Pattern KEY_PATTERN =
      Pattern.compile("((key|value)<(?<wrapped>.*?)>|(?<plain>\\S++))(?:\\s+|$)");

  public static List<String> splitArgs(@Nullable String args) {
    if (args == null || args.isEmpty()) {
      return emptyList();
    }

    List<String> argsList = new ArrayList<>();
    Matcher matcher = KEY_PATTERN.matcher(args);
    while (matcher.find()) {
      String wrapped = matcher.group("wrapped");
      if (wrapped != null) {
        argsList.add(wrapped);
      } else {
        argsList.add(matcher.group("plain"));
      }
    }
    return argsList;
  }

  private LettuceArgSplitter() {}
}
