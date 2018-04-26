package datadog.trace.instrumentation.sparkjava;

import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.DDAdvice;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.api.DDTags;
import io.opentracing.Scope;
import io.opentracing.util.GlobalTracer;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import spark.route.HttpMethod;
import spark.routematch.RouteMatch;

@AutoService(Instrumenter.class)
public class RoutesInstrumentation extends Instrumenter.Configurable {

  public RoutesInstrumentation() {
    super("sparkjava", "sparkjava-2.4");
  }

  @Override
  public boolean defaultEnabled() {
    return false;
  }

  @Override
  public AgentBuilder apply(final AgentBuilder agentBuilder) {
    return agentBuilder
        .type(named("spark.route.Routes"))
        .transform(
            DDAdvice.create()
                .advice(
                    named("find")
                        .and(takesArgument(0, named("spark.route.HttpMethod")))
                        .and(returns(named("spark.routematch.RouteMatch")))
                        .and(isPublic()),
                    RoutesAdvice.class.getName()))
        .asDecorator();
  }

  public static class RoutesAdvice {

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void routeMatchEnricher(
        @Advice.Argument(0) final HttpMethod method, @Advice.Return final RouteMatch routeMatch) {

      final Scope scope = GlobalTracer.get().scopeManager().active();
      if (scope != null && routeMatch != null) {
        final String resourceName = method.name().toUpperCase() + " " + routeMatch.getMatchUri();
        scope.span().setTag(DDTags.RESOURCE_NAME, resourceName);
      }
    }
  }
}
