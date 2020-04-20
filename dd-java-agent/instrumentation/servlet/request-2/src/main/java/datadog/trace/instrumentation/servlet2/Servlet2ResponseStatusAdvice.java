package datadog.trace.instrumentation.servlet2;

import datadog.trace.bootstrap.InstrumentationContext;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import net.bytebuddy.asm.Advice;

public class Servlet2ResponseStatusAdvice {
  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void onEnter(
      @Advice.This final HttpServletResponse response, @Advice.Argument(0) final Integer status) {
    InstrumentationContext.get(ServletResponse.class, Integer.class).put(response, status);
  }
}
