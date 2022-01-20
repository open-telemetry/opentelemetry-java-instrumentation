package io.opentelemetry.javaagent.instrumentation.jedis.v3_0.resolver;

import com.google.errorprone.annotations.Immutable;
import java.util.List;
import redis.clients.jedis.Protocol;

@Immutable
public interface CommandResolver {

  Protocol.Command getCommand();

  List<Object> resolveArgs(Object[] args);
}
