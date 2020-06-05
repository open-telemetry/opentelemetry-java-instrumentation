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
package io.opentelemetry.auto.semantic;

import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;

import io.opentelemetry.auto.semantic.client.SampleHttpClientSemanticSpan;
import io.opentelemetry.auto.semantic.client.SampleHttpClientSemanticTracer;
import io.opentelemetry.auto.semantic.server.SampleHttpServerSemanticSpan;
import io.opentelemetry.auto.semantic.server.SampleHttpServerSemanticTracer;
import io.opentelemetry.context.Scope;

class TypedTracerDemonstration {

  private void serverDemonstration() {
    final SampleHttpServerSemanticTracer tracer = new SampleHttpServerSemanticTracer();

    final SampleHttpServerSemanticSpan span = tracer.startSpan("request instance");
    // span.onRequest("request instance"); // implicitly called on start.

    try (final Scope scope = currentContextWith(span)) {
      // make request
      final String response = "response instance";

      span.end(response);
      // span.onResponse("response instance"); // implicitly called on end.
    } catch (final Exception ex) {
      span.end(ex);
    }
  }

  private void clientDemonstration() {
    final SampleHttpClientSemanticTracer tracer = new SampleHttpClientSemanticTracer();

    final SampleHttpClientSemanticSpan span = tracer.startSpan("request instance");
    // span.onRequest("request instance"); // implicitly called on start.

    try (final Scope scope = currentContextWith(span)) {
      // make request
      final String response = "response instance";

      span.end(response);
      // span.onResponse("response instance"); // implicitly called on end.
    } catch (final Exception ex) {
      span.end(ex);
    }
  }
}
