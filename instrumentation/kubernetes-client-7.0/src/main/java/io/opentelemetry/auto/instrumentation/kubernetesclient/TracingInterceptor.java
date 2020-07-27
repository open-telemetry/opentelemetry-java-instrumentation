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

package io.opentelemetry.auto.instrumentation.kubernetesclient;

import static io.opentelemetry.auto.instrumentation.kubernetesclient.KubernetesClientDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.kubernetesclient.KubernetesClientDecorator.TRACER;
import static io.opentelemetry.context.ContextUtils.withScopedContext;
import static io.opentelemetry.trace.Span.Kind.CLIENT;
import static io.opentelemetry.trace.TracingContextUtils.withSpan;

import io.grpc.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Response;

public class TracingInterceptor implements Interceptor {

  @Override
  public Response intercept(final Chain chain) throws IOException {

    KubernetesRequestDigest digest = KubernetesRequestDigest.parse(chain.request());

    Span span =
        TRACER
            .spanBuilder(digest.toString())
            .setSpanKind(CLIENT)
            .setAttribute("namespace", digest.getResourceMeta().getNamespace())
            .setAttribute("name", digest.getResourceMeta().getName())
            .startSpan();

    DECORATE.afterStart(span);
    DECORATE.onRequest(span, chain.request());

    Context context = withSpan(span, Context.current());

    Response response;
    try (Scope scope = withScopedContext(context)) {
      response = chain.proceed(chain.request());
    } catch (final Exception e) {
      DECORATE.onError(span, e);
      span.end();
      throw e;
    }

    DECORATE.onResponse(span, response);
    DECORATE.beforeFinish(span);
    span.end();

    return response;
  }
}
