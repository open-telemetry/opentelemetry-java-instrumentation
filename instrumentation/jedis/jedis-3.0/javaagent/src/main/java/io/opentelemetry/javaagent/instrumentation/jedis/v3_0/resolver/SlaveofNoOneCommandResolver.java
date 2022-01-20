package io.opentelemetry.javaagent.instrumentation.jedis.v3_0.resolver;

import static redis.clients.jedis.Protocol.Command.SLAVEOF;

import com.google.errorprone.annotations.Immutable;
import java.util.List;
import redis.clients.jedis.Protocol;

@Immutable
public class SlaveofNoOneCommandResolver extends DefaultCommandResolver {
  public SlaveofNoOneCommandResolver() {
    super(SLAVEOF);
  }

  @Override
  public List<Object> resolveArgs(Object[] args) {
    List<Object> result = super.resolveArgs(args);
    result.add(0, Protocol.Keyword.NO.raw);
    result.add(1, Protocol.Keyword.ONE.raw);
    return result;
  }
}
