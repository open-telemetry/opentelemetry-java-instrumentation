/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awssdk.v1_11;

import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.declaresField;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.handlers.RequestHandler2;
import io.opentelemetry.javaagent.instrumentation.api.InstrumentationContext;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.List;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * This instrumentation might work with versions before 1.11.0, but this was the first version that
 * is tested. It could possibly be extended earlier.
 */
public class AwsClientInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.amazonaws.AmazonWebServiceClient")
        .and(declaresField(named("requestHandler2s")));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isConstructor(), AwsClientInstrumentation.class.getName() + "$AwsClientAdvice");
  }

  public static class AwsClientAdvice {
    // Since we're instrumenting the constructor, we can't add onThrowable.
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void addHandler(
        @Advice.FieldValue("requestHandler2s") List<RequestHandler2> handlers) {
      boolean hasAgentHandler = false;
      for (RequestHandler2 handler : handlers) {
        if (handler instanceof TracingRequestHandler) {
          hasAgentHandler = true;
          break;
        }
      }
      if (!hasAgentHandler) {
        handlers.add(
            new TracingRequestHandler(
                InstrumentationContext.get(AmazonWebServiceRequest.class, RequestMeta.class)));
      }
    }
  }
}
