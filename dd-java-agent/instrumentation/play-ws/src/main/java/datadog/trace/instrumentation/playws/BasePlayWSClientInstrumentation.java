package datadog.trace.instrumentation.playws;

import static datadog.trace.agent.tooling.ClassLoaderMatcher.classLoaderHasNoResources;
import static datadog.trace.agent.tooling.bytebuddy.matcher.DDElementMatchers.hasInterface;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import datadog.trace.agent.tooling.Instrumenter;
import java.util.Map;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public abstract class BasePlayWSClientInstrumentation extends Instrumenter.Default {
  public BasePlayWSClientInstrumentation() {
    super("play-ws");
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderMatcher() {
    // Optimization for expensive typeMatcher.
    return not(
        classLoaderHasNoResources("play/shaded/ahc/org/asynchttpclient/AsyncHttpClient.class"));
  }

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    // CachingAsyncHttpClient rejects overrides to AsyncHandler
    // It also delegates to another AsyncHttpClient
    return nameStartsWith("play.")
        .<TypeDescription>and(
            hasInterface(named("play.shaded.ahc.org.asynchttpclient.AsyncHttpClient"))
                .and(not(named("play.api.libs.ws.ahc.cache.CachingAsyncHttpClient"))));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod()
            .and(named("execute"))
            .and(takesArguments(2))
            .and(takesArgument(0, named("play.shaded.ahc.org.asynchttpclient.Request")))
            .and(takesArgument(1, named("play.shaded.ahc.org.asynchttpclient.AsyncHandler"))),
        getClass().getName() + "$ClientAdvice");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "datadog.trace.agent.decorator.BaseDecorator",
      "datadog.trace.agent.decorator.ClientDecorator",
      "datadog.trace.agent.decorator.HttpClientDecorator",
      "datadog.trace.instrumentation.playws.PlayWSClientDecorator",
      "datadog.trace.instrumentation.playws.HeadersInjectAdapter",
      packageName + ".AsyncHandlerWrapper",
      packageName + ".StreamedAsyncHandlerWrapper"
    };
  }
}
