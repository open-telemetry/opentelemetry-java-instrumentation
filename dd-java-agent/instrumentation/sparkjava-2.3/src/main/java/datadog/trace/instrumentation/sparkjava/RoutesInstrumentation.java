package datadog.trace.instrumentation.sparkjava;

import static datadog.trace.agent.tooling.ClassLoaderMatcher.classLoaderHasClasses;
import static net.bytebuddy.matcher.ElementMatchers.*;

import datadog.trace.agent.tooling.DDAdvice;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.api.DDTags;
import io.opentracing.Scope;
import io.opentracing.Span;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import spark.route.HttpMethod;
import spark.routematch.RouteMatch;

public class RoutesInstrumentation extends Instrumenter.Configurable {

  public RoutesInstrumentation() {
    super("sparkjava", "sparkjava-2.3");
  }

  @Override
  public boolean defaultEnabled() {
    return false;
  }

  @Override
  public AgentBuilder apply(final AgentBuilder agentBuilder) {
    return agentBuilder
        .type(
            is(named("spark.route.Routes")),
            classLoaderHasClasses("spark.embeddedserver.jetty.EmbeddedJettyServer"))
        .transform(
            DDAdvice.create()
                .advice(
                    named("find")
                        .and(takesArgument(0, named("spark.route.HttpMethod")))
                        .and(takesArgument(1, named("String")))
                        .and(takesArgument(2, named("String")))
                        .and(isPublic()),
                    RoutesInstrumentationAdvice.class.getName()))
        .asDecorator();
  }

  public static class RoutesInstrumentationAdvice {

    @Advice.OnMethodExit()
    public static void routeMatchEnricher(
        @Advice.Argument(0) final HttpMethod method,
        @Advice.Enter final Scope scope,
        @Advice.Return final RouteMatch routeMatch) {
      if (scope != null && routeMatch != null) {
        final Span span = scope.span();
        final String resourceName = method.name() + " " + routeMatch.getMatchUri();
        span.setTag(DDTags.RESOURCE_NAME, resourceName);
      }
    }
  }
}
