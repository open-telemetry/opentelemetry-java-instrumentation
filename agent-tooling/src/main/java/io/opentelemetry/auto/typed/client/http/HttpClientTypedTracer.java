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

package io.opentelemetry.auto.typed.client.http;

import io.grpc.Context;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.typed.client.ClientTypedTracer;
import io.opentelemetry.context.propagation.HttpTextFormat;
import io.opentelemetry.trace.TracingContextUtils;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class HttpClientTypedTracer<
        T extends HttpClientTypedSpan<T, REQUEST, RESPONSE>, REQUEST, RESPONSE>
    extends ClientTypedTracer<T, REQUEST, RESPONSE> {

  @Override
  protected T startSpan(final REQUEST request, final T span) {
    Context context = TracingContextUtils.withSpan(span, Context.current());
    OpenTelemetry.getPropagators().getHttpTextFormat().inject(context, request, getSetter());
    return super.startSpan(request, span);
  }

  protected abstract HttpTextFormat.Setter<REQUEST> getSetter();
}
