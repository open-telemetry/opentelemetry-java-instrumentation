package io.opentelemetry.javaagent.instrumentation.jedis.v3_0.resolver;

import com.google.errorprone.annotations.Immutable;
import java.util.List;
import java.util.Map;
import redis.clients.jedis.Protocol;

@Immutable
public class SentinelSetCommandResolver extends DefaultCommandResolver {
  public SentinelSetCommandResolver(Protocol.Command command) {
    super(command);
  }

  @Override
  public List<Object> resolveArgs(Object[] args) {
    List<Object> result = super.resolveArgs(args);

    String masterName = (String) args[0];
    Map<String, String> parameterMap = (Map<String, String>) args[1];
    int index = 0;
    result.add(index++, Protocol.SENTINEL_SET);
    result.add(index++, masterName);
    for (Map.Entry<String, String> entry : parameterMap.entrySet()) {
      result.add(index++, entry.getKey());
      result.add(index++, entry.getValue());
    }
    return result;
  }
}
