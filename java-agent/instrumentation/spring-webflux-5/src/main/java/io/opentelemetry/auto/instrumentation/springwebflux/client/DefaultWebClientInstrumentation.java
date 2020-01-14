package io.opentelemetry.auto.instrumentation.springwebflux.client;

import static io.opentelemetry.auto.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.auto.tooling.Instrumenter;
import java.util.Map;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

// FIXME this instrumentation is not currently reliable, see DefaultWebClientAdvice
// @AutoService(Instrumenter.class)
public class DefaultWebClientInstrumentation extends Instrumenter.Default {

  public DefaultWebClientInstrumentation() {
    super("spring-webflux", "spring-webflux-client");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "io.opentelemetry.auto.decorator.BaseDecorator",
      "io.opentelemetry.auto.decorator.ClientDecorator",
      "io.opentelemetry.auto.decorator.HttpClientDecorator",
      packageName + ".SpringWebfluxHttpClientDecorator",
      packageName + ".HttpHeadersInjectAdapter",
      packageName + ".TracingClientResponseSubscriber",
      packageName + ".TracingClientResponseSubscriber$1",
      packageName + ".TracingClientResponseMono",
    };
  }

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return safeHasSuperType(
        named("org.springframework.web.reactive.function.client.ExchangeFunction"));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod()
            .and(isPublic())
            .and(named("exchange"))
            .and(
                takesArgument(
                    0, named("org.springframework.web.reactive.function.client.ClientRequest"))),
        packageName + ".DefaultWebClientAdvice");
  }
}
