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

package io.opentelemetry.instrumentation.auto.httpurlconnection;

import static io.opentelemetry.instrumentation.auto.httpurlconnection.HttpUrlConnectionTracer.TRACER;
import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.is;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.tracer.utils.NetPeerUtils;
import io.opentelemetry.instrumentation.auto.api.InternalJarURLHandler;
import io.opentelemetry.javaagent.tooling.Instrumenter;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Span.Kind;
import io.opentelemetry.trace.attributes.SemanticAttributes;
import java.net.URL;
import java.net.URLStreamHandler;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public class UrlInstrumentation extends Instrumenter.Default {

  public UrlInstrumentation() {
    super("urlconnection", "httpurlconnection");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return is(URL.class);
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod().and(isPublic()).and(named("openConnection")),
        UrlInstrumentation.class.getName() + "$ConnectionErrorAdvice");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".HttpUrlConnectionTracer",
    };
  }

  public static class ConnectionErrorAdvice {

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void errorSpan(
        @Advice.This URL url,
        @Advice.Thrown Throwable throwable,
        @Advice.FieldValue("handler") URLStreamHandler handler) {
      if (throwable != null) {
        // Various agent components end up calling `openConnection` indirectly
        // when loading classes. Avoid tracing these calls.
        boolean disableTracing = handler instanceof InternalJarURLHandler;
        if (disableTracing) {
          return;
        }

        String protocol = url.getProtocol();
        protocol = protocol != null ? protocol : "url";

        Span span = TRACER.startSpan(protocol + ".request", Kind.CLIENT);
        try (Scope scope = currentContextWith(span)) {
          span.setAttribute(SemanticAttributes.HTTP_URL.key(), url.toString());
          span.setAttribute(
              SemanticAttributes.NET_PEER_PORT.key(), url.getPort() == -1 ? 80 : url.getPort());
          String host = url.getHost();
          if (host != null && !host.isEmpty()) {
            NetPeerUtils.setAttributes(span, host, null);
          }

          TRACER.endExceptionally(span, throwable);
        }
      }
    }
  }
}
