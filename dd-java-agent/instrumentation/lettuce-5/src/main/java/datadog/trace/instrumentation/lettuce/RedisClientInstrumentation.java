package datadog.trace.instrumentation.lettuce;

import static net.bytebuddy.matcher.ElementMatchers.*;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.DDAdvice;
import datadog.trace.agent.tooling.DDTransformers;
import datadog.trace.agent.tooling.HelperInjector;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.api.DDTags;
import io.lettuce.core.*;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.util.Collections;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;

@AutoService(Instrumenter.class)
public final class RedisClientInstrumentation extends Instrumenter.Configurable {

  private static final HelperInjector REDIS_ASYNC_HELPERS =
      new HelperInjector(RedisAsyncCommandsInstrumentation.class.getPackage().getName() + ".RedisAsyncBiFunction");

  public static final String SERVICE_NAME = "redis";
  public static final String COMPONENT_NAME = SERVICE_NAME + "-client";
  public static final String RESOURCE_NAME_PREFIX = "CONNECT:";
  public static final String REDIS_URL_TAG_NAME = "db.redis.url";
  public static final String REDIS_DB_INDEX_TAG_NAME = "db.redis.dbIndex";

  public RedisClientInstrumentation() {
    super(SERVICE_NAME);
  }

  @Override
  protected boolean defaultEnabled() {
    return false;
  }

  @Override
  public AgentBuilder apply(final AgentBuilder agentBuilder) {
    return agentBuilder
        .type(
            named("io.lettuce.core.RedisClient")
                .and(hasSuperType(named("io.lettuce.core.AbstractRedisClient"))))
        .transform(DDTransformers.defaultTransformers())
        .transform(REDIS_ASYNC_HELPERS)
        .transform(
            DDAdvice.create()
                .advice(
                    isMethod()
                        .and(isPrivate())
                        .and(returns(named("io.lettuce.core.ConnectionFuture")))
                        .and(nameStartsWith("connect"))
                        .and(nameEndsWith("Async"))
                        .and(takesArgument(1, named("io.lettuce.core.RedisURI"))),
                    ConnectionFutureAdvice.class.getName()))
        .asDecorator();
  }

  public static class ConnectionFutureAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope startSpan(@Advice.Argument(1) final RedisURI redisURI) {
      final Scope scope = GlobalTracer.get().buildSpan(SERVICE_NAME + ".query").startActive(false);

      final Span span = scope.span();
      Tags.DB_TYPE.set(span, SERVICE_NAME);
      Tags.SPAN_KIND.set(span, Tags.SPAN_KIND_CLIENT);
      Tags.COMPONENT.set(span, COMPONENT_NAME);

      final String url =
          redisURI.getHost() + ":" + redisURI.getPort() + "/" + redisURI.getDatabase();
      span.setTag(REDIS_URL_TAG_NAME, url);
      span.setTag(REDIS_DB_INDEX_TAG_NAME, redisURI.getDatabase());
      span.setTag(DDTags.RESOURCE_NAME, RESOURCE_NAME_PREFIX + url);
      span.setTag(DDTags.SERVICE_NAME, SERVICE_NAME);
      span.setTag(DDTags.SPAN_TYPE, SERVICE_NAME);

      return scope;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Enter final Scope scope,
        @Advice.Thrown final Throwable throwable,
        @Advice.Return(readOnly = false) final ConnectionFuture connectionFuture) {
      if (throwable != null) {
        final Span span = scope.span();
        Tags.ERROR.set(span, true);
        span.log(Collections.singletonMap("error.object", throwable));
        scope.close();
        return;
      }

      // close spans on error or normal completion
      connectionFuture.handleAsync(new RedisAsyncBiFunction<>(scope.span()));
      scope.close();
    }
  }
}
