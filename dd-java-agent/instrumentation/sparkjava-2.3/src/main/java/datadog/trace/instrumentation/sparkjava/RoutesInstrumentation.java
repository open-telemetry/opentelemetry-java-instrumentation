package datadog.trace.instrumentation.sparkjava;

import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.api.DDTags;
import io.opentracing.Scope;
import io.opentracing.util.GlobalTracer;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import spark.route.HttpMethod;
import spark.routematch.RouteMatch;

@AutoService(Instrumenter.class)
public class RoutesInstrumentation extends Instrumenter.Default {

  public RoutesInstrumentation() {
    super("sparkjava", "sparkjava-2.4");
  }

  @Override
  public boolean defaultEnabled() {
    return false;
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("spark.route.Routes");
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        named("find")
            .and(takesArgument(0, named("spark.route.HttpMethod")))
            .and(returns(named("spark.routematch.RouteMatch")))
            .and(isPublic()),
        RoutesAdvice.class.getName());
  }

  public static class RoutesAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
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
