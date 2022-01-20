package io.opentelemetry.javaagent.instrumentation.jedis.v3_0.resolver;

import com.google.errorprone.annotations.Immutable;
import java.util.ArrayList;
import java.util.List;
import redis.clients.jedis.Protocol;

@Immutable
public class DefaultCommandResolver implements CommandResolver {
  protected final Protocol.Command command;

  public DefaultCommandResolver(Protocol.Command command) {
    this.command = command;
  }

  @Override
  public Protocol.Command getCommand() {
    return command;
  }

  @Override
  public List<Object> resolveArgs(Object[] args) {
    List<Object> result = new ArrayList<>();
    for (Object arg : args) {
      result.add(arg);
    }
    return result;
  }
}
