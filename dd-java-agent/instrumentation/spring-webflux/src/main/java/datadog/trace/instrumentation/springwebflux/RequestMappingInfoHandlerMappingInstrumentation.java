package datadog.trace.instrumentation.springwebflux;

import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isProtected;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import java.util.Map;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

// Provides a way to get the URL with path variables
@AutoService(Instrumenter.class)
public final class RequestMappingInfoHandlerMappingInstrumentation extends Instrumenter.Default {

  public static final String PACKAGE =
      RequestMappingInfoHandlerMappingInstrumentation.class.getPackage().getName();

  public RequestMappingInfoHandlerMappingInstrumentation() {
    super("spring-webflux", "spring-webflux-annotation");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {PACKAGE + ".DispatcherHandlerMonoBiConsumer"};
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.springframework.web.reactive.result.method.RequestMappingInfoHandlerMapping");
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod()
            .and(isProtected())
            .and(named("handleMatch"))
            .and(
                takesArgument(
                    0, named("org.springframework.web.reactive.result.method.RequestMappingInfo")))
            .and(takesArgument(1, named("org.springframework.web.method.HandlerMethod")))
            .and(takesArgument(2, named("org.springframework.web.server.ServerWebExchange")))
            .and(takesArguments(3)),
        // Cannot reference class directly here because it would lead to class load failure on Java7
        PACKAGE + ".RequestMappingInfoHandlerMappingAdvice");
  }
}
