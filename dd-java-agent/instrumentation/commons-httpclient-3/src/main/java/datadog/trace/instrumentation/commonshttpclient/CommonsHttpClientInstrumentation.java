package datadog.trace.instrumentation.commonshttpclient;

import static datadog.trace.agent.tooling.ClassLoaderMatcher.classLoaderHasNoResources;
import static datadog.trace.agent.tooling.bytebuddy.matcher.DDElementMatchers.extendsClass;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.propagate;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.startSpan;
import static datadog.trace.instrumentation.commonshttpclient.CommonsHttpClientDecorator.DECORATE;
import static datadog.trace.instrumentation.commonshttpclient.HttpHeadersInjectAdapter.SETTER;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.bootstrap.CallDepthThreadLocalMap;
import datadog.trace.bootstrap.instrumentation.api.AgentScope;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;

@AutoService(Instrumenter.class)
public class CommonsHttpClientInstrumentation extends Instrumenter.Default {

  public CommonsHttpClientInstrumentation() {
    super("commons-http-client");
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderMatcher() {
    // Optimization for expensive typeMatcher.
    return not(classLoaderHasNoResources("org/apache/commons/httpclient/HttpClient.class"));
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return extendsClass(named("org.apache.commons.httpclient.HttpClient"));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "datadog.trace.agent.decorator.BaseDecorator",
      "datadog.trace.agent.decorator.ClientDecorator",
      "datadog.trace.agent.decorator.HttpClientDecorator",
      packageName + ".CommonsHttpClientDecorator",
      packageName + ".HttpHeadersInjectAdapter",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {

    return singletonMap(
        isMethod()
            .and(named("executeMethod"))
            .and(takesArguments(3))
            .and(takesArgument(1, named("org.apache.commons.httpclient.HttpMethod"))),
        CommonsHttpClientInstrumentation.class.getName() + "$ExecAdvice");
  }

  public static class ExecAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AgentScope methodEnter(@Advice.Argument(1) final HttpMethod httpMethod) {
      final int callDepth = CallDepthThreadLocalMap.incrementCallDepth(HttpClient.class);
      if (callDepth > 0) {
        return null;
      }

      final AgentSpan span = startSpan("http.request");
      final AgentScope scope = activateSpan(span, true);

      DECORATE.afterStart(span);
      DECORATE.onRequest(span, httpMethod);

      final boolean awsClientCall =
          httpMethod.getRequestHeaders("amz-sdk-invocation-id").length > 0;
      // AWS calls are often signed, so we can't add headers without breaking the signature.
      if (!awsClientCall) {
        propagate().inject(span, httpMethod, SETTER);
      }
      return scope;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Enter final AgentScope scope,
        @Advice.Argument(1) final HttpMethod httpMethod,
        @Advice.Thrown final Throwable throwable) {

      if (scope == null) {
        return;
      }
      try {
        final AgentSpan span = scope.span();

        DECORATE.onResponse(span, httpMethod);
        DECORATE.onError(span, throwable);
        DECORATE.beforeFinish(span);
      } finally {
        scope.close();
        CallDepthThreadLocalMap.reset(HttpClient.class);
      }
    }
  }
}
