package io.opentelemetry.javaagent.instrumentation.jedis.v3_0.resolver;

import com.google.errorprone.annotations.Immutable;
import java.util.List;
import redis.clients.jedis.Protocol;
import redis.clients.jedis.util.SafeEncoder;

@Immutable
public class SubcommandResolver extends DefaultCommandResolver {
  private final String subcommand;

  public SubcommandResolver(Protocol.Command command, String subcommand) {
    super(command);
    this.subcommand = subcommand;
  }

  @Override
  public List<Object> resolveArgs(Object[] args) {
    List<Object> result = super.resolveArgs(args);
    result.add(0, SafeEncoder.encode(subcommand));
    return result;
  }
}
