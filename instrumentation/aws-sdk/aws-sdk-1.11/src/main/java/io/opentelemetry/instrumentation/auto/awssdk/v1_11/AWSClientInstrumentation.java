/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.instrumentation.auto.awssdk.v1_11;

import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.declaresField;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.handlers.RequestHandler2;
import com.google.auto.service.AutoService;
import io.opentelemetry.instrumentation.auto.api.InstrumentationContext;
import io.opentelemetry.javaagent.tooling.Instrumenter;
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
@AutoService(Instrumenter.class)
public final class AWSClientInstrumentation extends Instrumenter.Default {

  public AWSClientInstrumentation() {
    super("aws-sdk");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.amazonaws.AmazonWebServiceClient")
        .and(declaresField(named("requestHandler2s")));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".AwsSdkClientTracer",
      packageName + ".AwsSdkClientTracer$NamesCache",
      packageName + ".RequestMeta",
      packageName + ".TracingRequestHandler",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isConstructor(), AWSClientInstrumentation.class.getName() + "$AWSClientAdvice");
  }

  @Override
  public Map<String, String> contextStore() {
    return singletonMap("com.amazonaws.AmazonWebServiceRequest", packageName + ".RequestMeta");
  }

  public static class AWSClientAdvice {
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
