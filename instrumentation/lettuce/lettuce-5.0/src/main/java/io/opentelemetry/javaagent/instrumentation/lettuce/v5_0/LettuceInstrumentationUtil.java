/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.lettuce.v5_0;

import io.lettuce.core.protocol.RedisCommand;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class LettuceInstrumentationUtil {

  public static final String[] NON_INSTRUMENTING_COMMAND_WORDS =
      new String[] {"SHUTDOWN", "DEBUG", "OOM", "SEGFAULT"};

  public static final Set<String> nonInstrumentingCommands =
      new HashSet<>(Arrays.asList(NON_INSTRUMENTING_COMMAND_WORDS));

  /**
   * Determines whether a redis command should finish its relevant span early (as soon as tags are
   * added and the command is executed) because these commands have no return values/call backs, so
   * we must close the span early in order to provide info for the users
   *
   * @param command
   * @return false if the span should finish early (the command will not have a return value)
   */
  public static boolean expectsResponse(RedisCommand command) {
    String commandName = LettuceInstrumentationUtil.getCommandName(command);
    return !nonInstrumentingCommands.contains(commandName);
  }

  /**
   * Retrieves the actual redis command name from a RedisCommand object
   *
   * @param command the lettuce RedisCommand object
   * @return the redis command as a string
   */
  public static String getCommandName(RedisCommand command) {
    String commandName = "Redis Command";
    if (command != null) {

      // get the redis command name (i.e. GET, SET, HMSET, etc)
      if (command.getType() != null) {
        commandName = command.getType().name().trim();
      }
    }
    return commandName;
  }
}
