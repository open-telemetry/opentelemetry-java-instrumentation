package io.opentelemetry.javaagent.instrumentation.struts2;

import com.opensymphony.xwork2.ActionInvocation;
import com.opensymphony.xwork2.ActionProxy;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import io.opentelemetry.instrumentation.api.typedspan.HttpServerSpan;

public class Struts2Tracer extends BaseTracer {

  public static final Struts2Tracer TRACER = new Struts2Tracer();

  public Span startSpan(ActionInvocation actionInvocation) {
    updateHttpRouteInServerSpan(actionInvocation);

    Object action = actionInvocation.getAction();
    String namespace = action.getClass().getName();

    String method = actionInvocation.getProxy().getMethod();

    String spanName = spanNameForMethod(action.getClass(), method);

    Span strutsSpan = tracer.spanBuilder(spanName).startSpan();

    strutsSpan.setAttribute("action.name", actionInvocation.getProxy().getActionName());
    strutsSpan.setAttribute("code.namespace", namespace);
    if (method != null) {
      strutsSpan.setAttribute("code.function", method);
    }


    return strutsSpan;
  }

  private void updateHttpRouteInServerSpan(ActionInvocation actionInvocation) {
    Span serverSpan = getCurrentServerSpan();
    if (serverSpan instanceof HttpServerSpan) {
      ActionProxy proxy = actionInvocation.getProxy();
      String method = proxy.getMethod();
      ((HttpServerSpan)serverSpan).setHttpRoute(proxy.getNamespace() + proxy.getActionName() + (method != null ? "." + method : ""));
    }
    else {
      System.out.println("========== MY SERVER SPAN IS NOT HTTP!!!!! " + serverSpan.getClass().getName());
    }
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.struts-2";
  }
}
