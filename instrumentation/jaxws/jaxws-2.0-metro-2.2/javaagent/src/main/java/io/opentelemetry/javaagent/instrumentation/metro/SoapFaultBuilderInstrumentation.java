/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.metro;

import static io.opentelemetry.javaagent.instrumentation.metro.MetroJaxWsTracer.tracer;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.sun.xml.ws.api.message.Packet;
import com.sun.xml.ws.api.pipe.Fiber;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class SoapFaultBuilderInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return named("com.sun.xml.ws.fault.SOAPFaultBuilder");
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        named("createSOAPFaultMessage")
            .and(takesArgument(0, named("com.sun.xml.ws.api.SOAPVersion")))
            .and(takesArgument(1, named("com.sun.xml.ws.model.CheckedExceptionImpl")))
            .and(takesArgument(2, named(Throwable.class.getName()))),
        SoapFaultBuilderInstrumentation.class.getName() + "$CaptureThrowableAdvice");
  }

  public static class CaptureThrowableAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onEnter(@Advice.Argument(2) Throwable throwable) {
      if (throwable == null) {
        return;
      }
      Packet request = null;
      // we expect this to be called with attached fiber
      // if fiber is not attached current() throws IllegalStateException
      try {
        request = Fiber.current().getPacket();
      } catch (IllegalStateException ignore) {
        // fiber not available
      }
      if (request != null) {
        tracer().storeThrowable(request, throwable);
      }
    }
  }
}
