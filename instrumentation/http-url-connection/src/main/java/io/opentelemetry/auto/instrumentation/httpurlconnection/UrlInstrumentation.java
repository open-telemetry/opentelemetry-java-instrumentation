/*
 * Copyright 2020, OpenTelemetry Authors
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
package io.opentelemetry.auto.instrumentation.httpurlconnection;

import static io.opentelemetry.auto.instrumentation.httpurlconnection.HttpUrlConnectionDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.httpurlconnection.HttpUrlConnectionDecorator.TRACER;
import static io.opentelemetry.trace.Span.Kind.CLIENT;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.is;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.bootstrap.InternalJarURLHandler;
import io.opentelemetry.auto.config.Config;
import io.opentelemetry.auto.instrumentation.api.MoreTags;
import io.opentelemetry.auto.instrumentation.api.Tags;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import java.net.URL;
import java.net.URLStreamHandler;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public class UrlInstrumentation extends Instrumenter.Default {

  public static final String COMPONENT = "UrlConnection";

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
      packageName + ".HttpUrlConnectionDecorator",
    };
  }

  public static class ConnectionErrorAdvice {

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void errorSpan(
        @Advice.This final URL url,
        @Advice.Thrown final Throwable throwable,
        @Advice.FieldValue("handler") final URLStreamHandler handler) {
      if (throwable != null) {
        // Various agent components end up calling `openConnection` indirectly
        // when loading classes. Avoid tracing these calls.
        final boolean disableTracing = handler instanceof InternalJarURLHandler;
        if (disableTracing) {
          return;
        }

        String protocol = url.getProtocol();
        protocol = protocol != null ? protocol : "url";

        final Span span = TRACER.spanBuilder(protocol + ".request").setSpanKind(CLIENT).startSpan();
        span.setAttribute(Tags.COMPONENT, COMPONENT);

        try (final Scope scope = TRACER.withSpan(span)) {
          span.setAttribute(Tags.HTTP_URL, url.toString());
          span.setAttribute(MoreTags.NET_PEER_PORT, url.getPort() == -1 ? 80 : url.getPort());
          final String host = url.getHost();
          if (host != null && !host.isEmpty()) {
            span.setAttribute(MoreTags.NET_PEER_NAME, host);
            if (Config.get().isHttpClientSplitByDomain()) {
              span.setAttribute(MoreTags.SERVICE_NAME, host);
            }
          }

          DECORATE.onError(span, throwable);
          span.end();
        }
      }
    }
  }
}
