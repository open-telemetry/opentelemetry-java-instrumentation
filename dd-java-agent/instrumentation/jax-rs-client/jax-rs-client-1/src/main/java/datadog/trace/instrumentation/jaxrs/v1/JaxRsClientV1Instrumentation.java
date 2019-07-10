package datadog.trace.instrumentation.jaxrs.v1;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static datadog.trace.instrumentation.jaxrs.v1.JaxRsClientV1Decorator.DECORATE;
import com.google.auto.service.AutoService;
import com.sun.jersey.api.client.ClientHandler;
import com.sun.jersey.api.client.ClientRequest;
import com.sun.jersey.api.client.ClientResponse;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.api.DDTags;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.propagation.Format;
import io.opentracing.util.GlobalTracer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import static java.util.Collections.singletonMap;

import java.util.Map;

@AutoService(Instrumenter.class)
public final class JaxRsClientV1Instrumentation extends Instrumenter.Default {

  public JaxRsClientV1Instrumentation() {
    super("jax-rs", "jaxrs", "jax-rs-client");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return safeHasSuperType(named("com.sun.jersey.api.client.ClientHandler"));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "datadog.trace.agent.decorator.BaseDecorator",
      "datadog.trace.agent.decorator.ClientDecorator",
      "datadog.trace.agent.decorator.HttpClientDecorator",
      packageName + ".JaxRsClientDecorator",
      packageName + ".ClientTracingFeature",
      packageName + ".ClientTracingFilter",
      packageName + ".InjectAdapter",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        named("handle")
          .and(takesArgument(2, named("com.sun.jersey.api.client.ClientRequest")))
          .and(returns(named("com.sun.jersey.api.client.ClientResponse"))),
        HandleAdvice.class.getName()
    );
  }

  public static class HandleAdvice {

    @Advice.OnMethodEnter
    public static void onEnter(@Advice.Argument(value = 1) final ClientRequest request, ClientHandler thisObj) {

      // WARNING: this might be a chain...so we only have to trace the first in the chain.
      boolean isRootClientHandler = null == request.getProperties().get("dd.span");
      if (isRootClientHandler) {
        final Span span =
          GlobalTracer.get()
            .buildSpan("jax-rs.client.call")
            .withTag(DDTags.RESOURCE_NAME, request.getMethod() + " jax-rs.client.call")
            .start();
        try (final Scope scope = GlobalTracer.get().scopeManager().activate(span, false)) {
          DECORATE.afterStart(span);
          DECORATE.onRequest(span, request);

          GlobalTracer.get()
            .inject(
              span.context(),
              Format.Builtin.HTTP_HEADERS,
              new InjectAdapter(request.getHeaders()));

          request.getProperties().put("dd.span", span);
          request.getProperties().put("dd.root.handler.hash", thisObj.hashCode());
        }
      }
    }

    @Advice.OnMethodExit
    public static void onExit(@Advice.Argument(value = 1) final ClientRequest request, ClientResponse response,  ClientHandler thisObj) {
      Span span = (Span) request.getProperties().get("dd.span");
      if (null == span) {
        return;
      }

      request.getProperties().get("dd.root.handler.hash");

      if (thisObj.hashCode() == (Integer) request.getProperties().get("dd.root.handler.hash")) {
        // this is the root span, closing all the things
        DECORATE.onResponse(span, response);
        DECORATE.beforeFinish(span);
        span.finish();
      }
    }
  }
}
