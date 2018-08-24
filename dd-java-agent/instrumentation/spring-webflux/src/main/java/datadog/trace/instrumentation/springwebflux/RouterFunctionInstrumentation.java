package datadog.trace.instrumentation.springwebflux;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.declaresField;
import static net.bytebuddy.matcher.ElementMatchers.isAbstract;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import java.util.Collections;
import java.util.Map;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class RouterFunctionInstrumentation extends Instrumenter.Default {

  public static final String PACKAGE = RouterFunctionInstrumentation.class.getPackage().getName();

  public RouterFunctionInstrumentation() {
    super("spring-webflux");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      RouterFunctionInstrumentation.class.getPackage().getName()
          + ".DispatcherHandlerMonoBiConsumer"
    };
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return not(isAbstract())
        .and(declaresField(named("predicate")))
        .and(
            safeHasSuperType(
                named(
                    "org.springframework.web.reactive.function.server.RouterFunctions$AbstractRouterFunction")));
  }

  @Override
  public Map<ElementMatcher, String> transformers() {
    return Collections.<ElementMatcher, String>singletonMap(
        isMethod()
            .and(isPublic())
            .and(named("route"))
            .and(
                takesArgument(
                    0, named("org.springframework.web.reactive.function.server.ServerRequest")))
            .and(takesArguments(1)),
        // Cannot reference class directly here because it would lead to class load failure on Java7
        PACKAGE + ".RouterFunctionAdvice");
  }
}
