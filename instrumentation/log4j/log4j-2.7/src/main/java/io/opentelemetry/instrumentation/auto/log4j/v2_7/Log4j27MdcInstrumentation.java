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

package io.opentelemetry.instrumentation.auto.log4j.v2_7;

import static io.opentelemetry.instrumentation.api.log.LoggingContextConstants.SAMPLED;
import static io.opentelemetry.instrumentation.api.log.LoggingContextConstants.SPAN_ID;
import static io.opentelemetry.instrumentation.api.log.LoggingContextConstants.TRACE_ID;
import static io.opentelemetry.javaagent.tooling.ClassLoaderMatcher.hasClassesNamed;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.implementsInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.tooling.Instrumenter;
import io.opentelemetry.trace.SpanContext;
import io.opentelemetry.trace.TracingContextUtils;
import java.util.Collections;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner.Typing;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.logging.log4j.util.SortedArrayStringMap;
import org.apache.logging.log4j.util.StringMap;

@AutoService(Instrumenter.class)
public class Log4j27MdcInstrumentation extends Instrumenter.Default {
  public Log4j27MdcInstrumentation() {
    super("log4j2", "log4j", "log4j-2.7");
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderMatcher() {
    return not(hasClassesNamed("org.apache.logging.log4j.core.util.ContextDataProvider"));
  }

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return implementsInterface(named("org.apache.logging.log4j.core.ContextDataInjector"));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return Collections.singletonMap(
        isMethod()
            .and(named("injectContextData"))
            .and(returns(named("org.apache.logging.log4j.util.StringMap"))),
        Log4j27MdcInstrumentation.class.getName() + "$InjectContextDataAdvice");
  }

  public static class InjectContextDataAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.Return(typing = Typing.DYNAMIC, readOnly = false) StringMap contextData) {
      SpanContext currentContext = TracingContextUtils.getCurrentSpan().getContext();
      if (!currentContext.isValid()) {
        return;
      }

      if (contextData.containsKey(TRACE_ID)) {
        // Assume already instrumented event if traceId is present.
        return;
      }

      StringMap newContextData = new SortedArrayStringMap(contextData);
      newContextData.putValue(TRACE_ID, currentContext.getTraceIdAsHexString());
      newContextData.putValue(SPAN_ID, currentContext.getSpanIdAsHexString());
      if (currentContext.isSampled()) {
        newContextData.putValue(SAMPLED, "true");
      }
      contextData = newContextData;
    }
  }
}
