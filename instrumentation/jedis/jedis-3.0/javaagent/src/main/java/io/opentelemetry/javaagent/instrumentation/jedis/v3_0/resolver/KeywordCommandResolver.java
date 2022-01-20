package io.opentelemetry.javaagent.instrumentation.jedis.v3_0.resolver;

import com.google.errorprone.annotations.Immutable;
import java.util.List;
import redis.clients.jedis.Protocol;

@Immutable
public class KeywordCommandResolver extends DefaultCommandResolver {
  private final Protocol.Keyword keyword;

  public KeywordCommandResolver(Protocol.Command command, Protocol.Keyword keyword) {
    super(command);
    this.keyword = keyword;
  }

  @Override
  public List<Object> resolveArgs(Object[] args) {
    List<Object> result = super.resolveArgs(args);
    result.add(0, keyword.raw);
    return result;
  }
}
