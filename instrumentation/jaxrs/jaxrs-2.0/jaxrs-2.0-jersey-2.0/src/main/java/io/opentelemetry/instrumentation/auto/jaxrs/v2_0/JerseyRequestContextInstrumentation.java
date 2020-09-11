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

import com.google.auto.service.AutoService;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.tooling.Instrumenter;
import io.opentelemetry.trace.Span;
import java.lang.reflect.Method;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.UriInfo;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.Local;

/**
 * Jersey specific context instrumentation.
 *
 * <p>JAX-RS does not define a way to get the matched resource method from the <code>
 * ContainerRequestContext</code>
 *
 * <p>In the Jersey implementation, <code>UriInfo</code> implements <code>ResourceInfo</code>. The
 * matched resource method can be retrieved from that object
 */
@AutoService(Instrumenter.class)
public class JerseyRequestContextInstrumentation extends AbstractRequestContextInstrumentation {
  public static class ContainerRequestContextAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void decorateAbortSpan(
        @Advice.This ContainerRequestContext context,
        @Local("otelSpan") Span span,
        @Local("otelScope") Scope scope) {
      UriInfo uriInfo = context.getUriInfo();

      if (context.getProperty(JaxRsAnnotationsTracer.ABORT_HANDLED) == null
          && uriInfo instanceof ResourceInfo) {

        ResourceInfo resourceInfo = (ResourceInfo) uriInfo;
        Method method = resourceInfo.getResourceMethod();
        Class<?> resourceClass = resourceInfo.getResourceClass();

        span = RequestFilterHelper.createOrUpdateAbortSpan(context, resourceClass, method);
        if (span != null) {
          scope = TRACER.startScope(span);
        }
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Local("otelSpan") Span span,
        @Local("otelScope") Scope scope,
        @Advice.Thrown Throwable throwable) {
      RequestFilterHelper.closeSpanAndScope(span, scope, throwable);
    }
  }
}
