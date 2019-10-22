package datadog.trace.instrumentation.springwebflux.server;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isAbstract;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import java.util.Map;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class RouterFunctionInstrumentation extends AbstractWebfluxInstrumentation {

  public RouterFunctionInstrumentation() {
    super("spring-webflux-functional");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return not(isAbstract())
        .and(
            safeHasSuperType(
                // TODO: this doesn't handle nested routes (DefaultNestedRouterFunction)
                named(
                    "org.springframework.web.reactive.function.server.RouterFunctions$DefaultRouterFunction")));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod()
            .and(isPublic())
            .and(named("route"))
            .and(
                takesArgument(
                    0, named("org.springframework.web.reactive.function.server.ServerRequest")))
            .and(takesArguments(1)),
        // Cannot reference class directly here because it would lead to class load failure on Java7
        packageName + ".RouterFunctionAdvice");
  }
}
