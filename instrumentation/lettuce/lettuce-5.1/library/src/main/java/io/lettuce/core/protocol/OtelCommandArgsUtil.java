/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.lettuce.core.protocol;

import io.lettuce.core.protocol.CommandArgs.KeyArgument;
import io.lettuce.core.protocol.CommandArgs.SingularArgument;
import io.lettuce.core.protocol.CommandArgs.ValueArgument;
import io.opentelemetry.instrumentation.lettuce.common.LettuceArgSplitter;
import java.util.ArrayList;
import java.util.List;

// Helper class for accessing package private fields in CommandArgs and its inner classes.
// https://github.com/lettuce-io/lettuce-core/blob/main/src/main/java/io/lettuce/core/protocol/CommandArgs.java
public final class OtelCommandArgsUtil {

  /**
   * Extract argument {@link List} from {@link CommandArgs} so that we wouldn't need to parse them
   * from command {@link String} with {@link LettuceArgSplitter#splitArgs}.
   */
  public static List<String> getCommandArgs(CommandArgs<?, ?> commandArgs) {
    List<String> result = new ArrayList<>();
    for (SingularArgument argument : commandArgs.singularArguments) {
      String value = argument.toString();
      if (argument instanceof KeyArgument && value.startsWith("key<") && value.endsWith(">")) {
        value = value.substring("key<".length(), value.length() - 1);
      } else if (argument instanceof ValueArgument
          && value.startsWith("value<")
          && value.endsWith(">")) {
        value = value.substring("value<".length(), value.length() - 1);
      }
      result.add(value);
    }
    return result;
  }

  private OtelCommandArgsUtil() {}
}
