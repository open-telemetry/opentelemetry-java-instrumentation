package datadog.trace.instrumentation.jedis;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isProtected;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.DDAdvice;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.api.DDTags;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.util.Collections;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import redis.clients.jedis.Protocol.Command;

@AutoService(Instrumenter.class)
public final class JedisInstrumentation extends Instrumenter.Configurable {

  private static final String SERVICE_NAME = "redis";
  private static final String COMPONENT_NAME = SERVICE_NAME + "-command";

  public JedisInstrumentation() {
    super(SERVICE_NAME);
  }

  @Override
  protected boolean defaultEnabled() {
    return false;
  }

  @Override
  public AgentBuilder apply(final AgentBuilder agentBuilder) {
    return agentBuilder
        .type(not(isInterface()).and(hasSuperType(named("redis.clients.jedis.Connection"))))
        .transform(
            DDAdvice.create()
                .advice(
                    isMethod()
                        .and(isProtected())
                        .and(nameStartsWith("sendCommand"))
                        .and(takesArgument(1, byte[][].class)),
                    JedisAdvice.class.getName()))
        .asDecorator();
  }

  public static class JedisAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope nameResource(@Advice.Argument(0) final Command command) {

      final Scope scope = GlobalTracer.get().buildSpan("redis.command").startActive(true);

      final Span span = scope.span();
      Tags.DB_TYPE.set(span, SERVICE_NAME);
      Tags.SPAN_KIND.set(span, Tags.SPAN_KIND_CLIENT);
      Tags.COMPONENT.set(span, COMPONENT_NAME);

      span.setTag(DDTags.RESOURCE_NAME, command.name());
      span.setTag(DDTags.SERVICE_NAME, SERVICE_NAME);
      span.setTag(DDTags.SPAN_TYPE, SERVICE_NAME);

      return scope;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Enter final Scope scope, @Advice.Thrown final Throwable throwable) {
      if (throwable != null) {
        final Span span = scope.span();
        Tags.ERROR.set(span, true);
        span.log(Collections.singletonMap("error.object", throwable));
      }
      scope.close();
    }
  }
}
