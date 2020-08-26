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

package io.opentelemetry.javaagent.typed.client.http;

import io.grpc.Context;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.javaagent.typed.client.ClientTypedTracer;
import io.opentelemetry.trace.TracingContextUtils;

public abstract class HttpClientTypedTracer<
        T extends HttpClientTypedSpan<T, REQUEST, RESPONSE>, REQUEST, RESPONSE>
    extends ClientTypedTracer<T, REQUEST, RESPONSE> {

  @Override
  protected T startSpan(REQUEST request, T span) {
    Context context = TracingContextUtils.withSpan(span, Context.current());
    OpenTelemetry.getPropagators().getTextMapPropagator().inject(context, request, getSetter());
    return super.startSpan(request, span);
  }

  protected abstract TextMapPropagator.Setter<REQUEST> getSetter();
}
