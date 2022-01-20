package io.opentelemetry.javaagent.instrumentation.jedis.v3_0.resolver;

import static redis.clients.jedis.Protocol.Command.CLUSTER;

import com.google.errorprone.annotations.Immutable;
import java.util.List;
import redis.clients.jedis.Protocol;
import redis.clients.jedis.util.SafeEncoder;

@Immutable
public class ClusterSetSlotCommandResolver extends SubcommandResolver {
  private final String setSlotOperator;

  public ClusterSetSlotCommandResolver(String setSlotOperator) {
    super(CLUSTER, Protocol.CLUSTER_SETSLOT);
    this.setSlotOperator = setSlotOperator;
  }

  @Override
  public List<Object> resolveArgs(Object[] args) {
    List<Object> result = super.resolveArgs(args);
    result.add(2, SafeEncoder.encode(setSlotOperator));
    return result;
  }
}
