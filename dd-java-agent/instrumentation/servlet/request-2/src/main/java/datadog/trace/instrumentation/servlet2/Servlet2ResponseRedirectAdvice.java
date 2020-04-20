package datadog.trace.instrumentation.servlet2;

import datadog.trace.bootstrap.InstrumentationContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.bytebuddy.asm.Advice;

public class Servlet2ResponseRedirectAdvice {
  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void onEnter(@Advice.This final HttpServletResponse response) {
    InstrumentationContext.get(HttpServletResponse.class, HttpServletRequest.class)
        .get(response)
        .setAttribute("status-code", 302);
  }
}
