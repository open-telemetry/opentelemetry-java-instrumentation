package datadog.trace.instrumentation.lettuce;

import io.lettuce.core.protocol.RedisCommand;
import java.util.*;

public class LettuceInstrumentationUtil {

  public static final String SERVICE_NAME = "redis";
  public static final String COMPONENT_NAME = SERVICE_NAME + "-client";
  public static final String MAP_KEY_CMD_NAME = "CMD_NAME";
  public static final String MAP_KEY_CMD_ARGS = "CMD_ARGS";

  public static final String[] NON_INSTRUMENTING_COMMAND_WORDS =
      new String[] {"SHUTDOWN", "DEBUG", "OOM", "SEGFAULT"};
  public static final Set<String> nonInstrumentingCommands =
      new HashSet<>(Arrays.asList(NON_INSTRUMENTING_COMMAND_WORDS));

  public static boolean doFinishSpanEarly(Map<String, String> commandMap) {
    String cmdName = commandMap.get(MAP_KEY_CMD_NAME);
    String cmdArgs = commandMap.get(MAP_KEY_CMD_ARGS);

    if (cmdName.equals("SHUTDOWN")
        || (nonInstrumentingCommands.contains(cmdName)
            && nonInstrumentingCommands.contains(cmdArgs))) {
      return true;
    }
    return false;
  }

  public static Map<String, String> getCommandInfo(RedisCommand command) {
    String commandName = "Redis Command";
    String commandArgs = null;
    Map<String, String> commandMap = new HashMap<>();
    if (command != null) {
      // get the arguments passed into the redis command
      if (command.getArgs() != null) {
        // standardize to null instead of using empty string
        commandArgs = command.getArgs().toCommandString();
        if ("".equals(commandArgs)) {
          commandArgs = null;
        }
      }
      // get the redis command name (i.e. GET, SET, HMSET, etc)
      if (command.getType() != null) {
        commandName = command.getType().name();
        // if it is an AUTH command, then remove the extracted command arguments since it is the password
        if ("AUTH".equals(commandName)) {
          commandArgs = null;
        }
      }
    }
    commandMap.put(MAP_KEY_CMD_NAME, commandName);
    commandMap.put(MAP_KEY_CMD_ARGS, commandArgs);
    return commandMap;
  }
}
