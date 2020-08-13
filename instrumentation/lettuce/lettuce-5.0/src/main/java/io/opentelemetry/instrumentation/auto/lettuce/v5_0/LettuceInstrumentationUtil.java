/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.auto.instrumentation.lettuce.v5_0;

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
  public static boolean expectsResponse(final RedisCommand command) {
    String commandName = LettuceInstrumentationUtil.getCommandName(command);
    return !nonInstrumentingCommands.contains(commandName);
  }

  /**
   * Retrieves the actual redis command name from a RedisCommand object
   *
   * @param command the lettuce RedisCommand object
   * @return the redis command as a string
   */
  public static String getCommandName(final RedisCommand command) {
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
