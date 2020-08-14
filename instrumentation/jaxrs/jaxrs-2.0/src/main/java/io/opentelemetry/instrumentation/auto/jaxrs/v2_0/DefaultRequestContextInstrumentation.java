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

package io.opentelemetry.instrumentation.auto.jaxrs.v2_0;

import static io.opentelemetry.instrumentation.auto.jaxrs.v2_0.JaxRsAnnotationsTracer.TRACER;
import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.instrumentation.auto.api.SpanWithScope;
import io.opentelemetry.trace.Span;
import java.lang.reflect.Method;
import javax.ws.rs.container.ContainerRequestContext;
import net.bytebuddy.asm.Advice;

/**
 * Default context instrumentation.
 *
 * <p>JAX-RS does not define a way to get the matched resource method from the <code>
 * ContainerRequestContext</code>
 *
 * <p>This default instrumentation uses the class name of the filter to create the span. More
 * specific instrumentations may override this value.
 */
@AutoService(Instrumenter.class)
public class DefaultRequestContextInstrumentation extends AbstractRequestContextInstrumentation {
  public static class ContainerRequestContextAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static SpanWithScope createGenericSpan(
        @Advice.This final ContainerRequestContext context) {

      if (context.getProperty(JaxRsAnnotationsTracer.ABORT_HANDLED) == null) {
        Class filterClass = (Class) context.getProperty(JaxRsAnnotationsTracer.ABORT_FILTER_CLASS);
        Method method = null;
        try {
          method = filterClass.getMethod("filter", ContainerRequestContext.class);
        } catch (final NoSuchMethodException e) {
          // Unable to find the filter method.  This should not be reachable because the context
          // can only be aborted inside the filter method
        }

        Span span = TRACER.startSpan(filterClass, method);

        return new SpanWithScope(span, currentContextWith(span));
      }

      return null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Enter final SpanWithScope spanWithScope, @Advice.Thrown final Throwable throwable) {
      if (spanWithScope == null) {
        return;
      }

      Span span = spanWithScope.getSpan();
      if (throwable != null) {
        TRACER.endExceptionally(span, throwable);
      } else {
        TRACER.end(span);
      }

      spanWithScope.closeScope();
    }
  }
}
