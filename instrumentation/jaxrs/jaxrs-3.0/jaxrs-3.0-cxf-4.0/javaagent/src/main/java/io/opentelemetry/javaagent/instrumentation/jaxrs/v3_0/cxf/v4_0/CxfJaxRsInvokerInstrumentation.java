/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v3_0.cxf.v4_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.takesArguments;
import static net.bytebuddy.matcher.ElementMatchers.declaresMethod;
import static net.bytebuddy.matcher.ElementMatchers.is;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.cxf.jaxrs.model.OperationResourceInfoStack;
import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.Message;

class CxfJaxRsInvokerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    // Both CXF 3 and 4 have this invoker class, including on mixed API classpaths.
    return named("org.apache.cxf.jaxrs.JAXRSInvoker")
        .and(
            declaresMethod(
                named("getTemplateValues")
                    .and(returns(named("jakarta.ws.rs.core.MultivaluedMap")))));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("invoke")
            .and(
                takesArguments(
                    named("org.apache.cxf.message.Exchange"), is(Object.class), is(Object.class))),
        getClass().getName() + "$InvokeAdvice");
  }

  @SuppressWarnings("unused")
  public static class InvokeAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static void onEnter(@Advice.Argument(0) Exchange exchange) {
      Message message = exchange.getInMessage();
      OperationResourceInfoStack matchedResources = message.get(OperationResourceInfoStack.class);
      if (matchedResources != null && !matchedResources.isEmpty()) {
        CxfSpanName.updateServerRoute(message, matchedResources);
      }
    }
  }
}
