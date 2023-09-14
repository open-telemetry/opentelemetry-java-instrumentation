package io.opentelemetry.javaagent.instrumentation.sling;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.sling.api.SlingHttpServletRequest;

import javax.servlet.Servlet;

import java.util.Deque;
import java.util.LinkedList;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.sling.SlingSingletons.REQUEST_ATTR_RESOLVED_SERVLET_NAME;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public class ServletResolverInstrumentation  implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("org.apache.sling.api.servlets.ServletResolver"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("resolveServlet"))
            .and(takesArguments(1))
            .and(takesArgument(0, named("org.apache.sling.api.SlingHttpServletRequest"))),
          this.getClass().getName()+"$ResolveServletAdvice");
  }

  @SuppressWarnings("unused")
  public static class ResolveServletAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(0) SlingHttpServletRequest request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {

//      Context parentContext = Java8BytecodeBridge.currentContext();
//
//      if (!helper().shouldStart(parentContext, request)) {
//        return;
//      }
//
//      context = helper().start(parentContext, request);
//      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void onExit(
        @Advice.Argument(0) SlingHttpServletRequest request,
        @Advice.Return Servlet servlet,
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {

//      if (scope == null) {
//        return;
//      }
//      scope.close();

      // TODO - copied from RequestUtil
      String name = null;

      if (servlet.getServletConfig() != null) {
        name = servlet.getServletConfig().getServletName();
      }
      if (name == null || name.isEmpty()) {
        name = servlet.getServletInfo();
      }
      if (name == null || name.isEmpty()) {
        name = servlet.getClass().getName();
      }

      @SuppressWarnings("unchecked")
      Deque<String> servletNames = (Deque<String>) request.getAttribute(REQUEST_ATTR_RESOLVED_SERVLET_NAME);
      if ( servletNames == null ) {
        servletNames = new LinkedList<>();
        request.setAttribute(REQUEST_ATTR_RESOLVED_SERVLET_NAME, servletNames);
      }
      servletNames.addLast(name);
      System.out.format("SLING TRACE resolved name %s for servlet %s; current stack is %s%n", name, servlet, servletNames);

//      Span.fromContext(context).updateName(name);
//      HttpServerRoute.update(context, HttpServerRouteSource.CONTROLLER, name);
//
//      helper().end(context, request, null, throwable);
    }
  }
}
