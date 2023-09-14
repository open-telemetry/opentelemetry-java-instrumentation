package io.opentelemetry.javaagent.instrumentation.sling;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.instrumentation.sling.SlingSingletons.REQUEST_ATTR_RESOLVED_SERVLET_NAME;
import static io.opentelemetry.javaagent.instrumentation.sling.SlingSingletons.helper;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerRoute;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerRouteSource;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.sling.api.SlingHttpServletRequest;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.util.Deque;

public class SlingSafeMethodsServletInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return AgentElementMatchers.implementsInterface(named("javax.servlet.Servlet"));
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("org.apache.sling.api.SlingHttpServletRequest");
  }

  @Override
  public void transform(TypeTransformer transformer) {

    String adviceClassName = this.getClass().getName() + "$ServiceServletAdvice";
    transformer.applyAdviceToMethod(
        named("service")
            .and(takesArguments(2))
            .and(takesArgument(0, named("javax.servlet.ServletRequest")))
            .and(takesArgument(1, named("javax.servlet.ServletResponse"))),
        adviceClassName);
  }

  @SuppressWarnings("unused")
  public static class ServiceServletAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(0) ServletRequest request,
        @Advice.Argument(1) ServletResponse response,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {

      if ( !(request instanceof SlingHttpServletRequest) ) {
        return;
      }

      System.out.format("SLING TRACE Handling request %s%n", request);

      SlingHttpServletRequest slingRequest = (SlingHttpServletRequest) request;

      Context parentContext = Java8BytecodeBridge.currentContext();

      if (!helper().shouldStart(parentContext, slingRequest)) {
        System.out.format("SLING TRACE should not handle %s%n", request);
        return;
      }

      context = helper().start(parentContext, slingRequest);
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void onExit(
        @Advice.Argument(0) ServletRequest request,
        @Advice.Argument(1) ServletResponse response,
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {

      System.out.format("SLING TRACE on exit for %s%n", request);

      if (scope == null) {
        return;
      }
      scope.close();

      SlingHttpServletRequest slingRequest = (SlingHttpServletRequest) request;
      // written by ServletResolverInstrumentation
      Object servletNameStack = request.getAttribute(REQUEST_ATTR_RESOLVED_SERVLET_NAME);

      System.out.format("SLING TRACE servletName attr for %s is %s%n", request, servletNameStack);

      if ( servletNameStack instanceof Deque<?>) {
        Deque<?> nameStack = (Deque<?>) servletNameStack;
        if ( ! nameStack.isEmpty() ) {
          String servletName = (String) nameStack.removeLast();
          Span.fromContext(context).updateName(servletName);
          HttpServerRoute.update(context, HttpServerRouteSource.CONTROLLER,servletName);
        }
      }
      helper().end(context, slingRequest, null, throwable);
    }
  }
}
