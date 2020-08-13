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

package io.opentelemetry.auto.typed;

import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;

import io.opentelemetry.auto.typed.client.SampleHttpClientTypedSpan;
import io.opentelemetry.auto.typed.client.SampleHttpClientTypedTracer;
import io.opentelemetry.auto.typed.server.SampleHttpServerTypedSpan;
import io.opentelemetry.auto.typed.server.SampleHttpServerTypedTracer;
import io.opentelemetry.context.Scope;

class TypedTracerDemonstration {

  private void serverDemonstration() {
    SampleHttpServerTypedTracer tracer = new SampleHttpServerTypedTracer();

    SampleHttpServerTypedSpan span = tracer.startSpan("request instance");
    // span.onRequest("request instance"); // implicitly called on start.

    try (Scope scope = currentContextWith(span)) {
      // make request
      String response = "response instance";

      span.end(response);
      // span.onResponse("response instance"); // implicitly called on end.
    } catch (final Exception ex) {
      span.end(ex);
    }
  }

  private void clientDemonstration() {
    SampleHttpClientTypedTracer tracer = new SampleHttpClientTypedTracer();

    SampleHttpClientTypedSpan span = tracer.startSpan("request instance");
    // span.onRequest("request instance"); // implicitly called on start.

    try (Scope scope = currentContextWith(span)) {
      // make request
      String response = "response instance";

      span.end(response);
      // span.onResponse("response instance"); // implicitly called on end.
    } catch (final Exception ex) {
      span.end(ex);
    }
  }
}
