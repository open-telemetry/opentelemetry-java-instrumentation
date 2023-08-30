/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.lettuce.core.protocol;

import io.lettuce.core.codec.StringCodec;
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
    StringCodec stringCodec = new StringCodec();

    for (SingularArgument argument : commandArgs.singularArguments) {
      String value = getArgValue(stringCodec, argument);
      result.add(value);
    }
    return result;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static String getArgValue(StringCodec stringCodec, SingularArgument argument) {
    if (argument instanceof KeyArgument) {
      KeyArgument keyArg = (KeyArgument) argument;
      return stringCodec.decodeKey(keyArg.codec.encodeKey(keyArg.key));
    }
    if (argument instanceof ValueArgument) {
      ValueArgument valueArg = (ValueArgument) argument;
      return stringCodec.decodeValue(valueArg.codec.encodeValue(valueArg.val));
    }
    return argument.toString();
  }

  private OtelCommandArgsUtil() {}
}
