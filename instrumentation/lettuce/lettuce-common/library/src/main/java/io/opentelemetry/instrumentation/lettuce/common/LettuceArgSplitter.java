/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

public final class LettuceArgSplitter {
  private static final Pattern KEY_PATTERN =
      Pattern.compile("((key|value)<(?<wrapped>[^>]+)>|(?<plain>[0-9A-Za-z=]+))(\\s+|$)");

  // this method removes the key|value<...> wrappers around redis keys or values and splits the args
  // string
  public static List<String> splitArgs(@Nullable String args) {
    if (args == null || args.isEmpty()) {
      return Collections.emptyList();
    }

    List<String> argsList = new ArrayList<>();
    Matcher m = KEY_PATTERN.matcher(args);
    while (m.find()) {
      String wrapped = m.group("wrapped");
      if (wrapped != null) {
        argsList.add(wrapped);
      } else {
        argsList.add(m.group("plain"));
      }
    }
    return argsList;
  }

  private LettuceArgSplitter() {}
}
