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

package io.opentelemetry.auto.instrumentation.jaxrs.v2_0;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.instrumentation.auto.api.SpanWithScope;
import java.lang.reflect.Method;
import javax.ws.rs.container.ContainerRequestContext;
import net.bytebuddy.asm.Advice;
import org.jboss.resteasy.core.ResourceMethodInvoker;
import org.jboss.resteasy.core.interception.PostMatchContainerRequestContext;

/**
 * RESTEasy specific context instrumentation.
 *
 * <p>JAX-RS does not define a way to get the matched resource method from the <code>
 * ContainerRequestContext</code>
 *
 * <p>In the RESTEasy implementation, <code>ContainerRequestContext</code> is implemented by <code>
 * PostMatchContainerRequestContext</code>. This class provides a way to get the matched resource
 * method through <code>getResourceMethod()</code>.
 */
@AutoService(Instrumenter.class)
public class Resteasy30RequestContextInstrumentation extends AbstractRequestContextInstrumentation {
  public static class ContainerRequestContextAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static SpanWithScope decorateAbortSpan(
        @Advice.This final ContainerRequestContext context) {
      if (context.getProperty(JaxRsAnnotationsTracer.ABORT_HANDLED) == null
          && context instanceof PostMatchContainerRequestContext) {

        ResourceMethodInvoker resourceMethodInvoker =
            ((PostMatchContainerRequestContext) context).getResourceMethod();
        Method method = resourceMethodInvoker.getMethod();
        Class resourceClass = resourceMethodInvoker.getResourceClass();

        return RequestFilterHelper.createOrUpdateAbortSpan(context, resourceClass, method);
      }

      return null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Enter final SpanWithScope scope, @Advice.Thrown final Throwable throwable) {
      RequestFilterHelper.closeSpanAndScope(scope, throwable);
    }
  }
}
