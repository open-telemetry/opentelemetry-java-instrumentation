package datadog.trace.instrumentation.lettuce.rx;

import io.lettuce.core.protocol.RedisCommand;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.bytebuddy.asm.Advice;
import reactor.core.publisher.Mono;

public class MonoCreationAdvice {

  public static final String SERVICE_NAME = "redis";
  public static final String COMPONENT_NAME = SERVICE_NAME + "-client";
  public static final String MAP_KEY_CMD_NAME = "CMD_NAME";
  public static final String MAP_KEY_CMD_ARGS = "CMD_ARGS";

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static Map<String, String> extractCommand(
      @Advice.Argument(0) final Supplier<RedisCommand> supplier) {
    RedisCommand command = supplier.get();

    String commandName = "Redis Command";
    String commandArgs = null;
    Map<String, String> commandMap = new HashMap<>();
    if (command != null) {
      // get the arguments passed into the redis command
      if (command.getArgs() != null) {
        commandArgs = command.getArgs().toCommandString();
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

  // throwables wouldn't matter here, because no spans have been started due to redis command not being
  // run until the user subscribes to the Mono publisher
  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void monitorSpan(
      @Advice.Enter final Map<String, String> commandMap,
      @Advice.Return(readOnly = false) Mono<?> publisher) {

    MonoDualConsumer mdc = new MonoDualConsumer(commandMap);
    publisher = publisher.doOnSubscribe(mdc);
    publisher = publisher.doOnSuccessOrError(mdc);
  }
}
