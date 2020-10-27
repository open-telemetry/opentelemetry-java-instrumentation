/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.typed;

import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.typed.client.SampleHttpClientTypedSpan;
import io.opentelemetry.javaagent.typed.client.SampleHttpClientTypedTracer;
import io.opentelemetry.javaagent.typed.server.SampleHttpServerTypedSpan;
import io.opentelemetry.javaagent.typed.server.SampleHttpServerTypedTracer;

class TypedTracerDemonstration {

  private void serverDemonstration() {
    SampleHttpServerTypedTracer tracer = new SampleHttpServerTypedTracer();

    SampleHttpServerTypedSpan span = tracer.startSpan("request instance");
    // span.onRequest("request instance"); // implicitly called on start.

    try (Scope scope = span.makeCurrent()) {
      // make request
      String response = "response instance";

      span.end(response);
      // span.onResponse("response instance"); // implicitly called on end.
    } catch (Exception ex) {
      span.end(ex);
    }
  }

  private void clientDemonstration() {
    SampleHttpClientTypedTracer tracer = new SampleHttpClientTypedTracer();

    SampleHttpClientTypedSpan span = tracer.startSpan("request instance");
    // span.onRequest("request instance"); // implicitly called on start.

    try (Scope scope = span.makeCurrent()) {
      // make request
      String response = "response instance";

      span.end(response);
      // span.onResponse("response instance"); // implicitly called on end.
    } catch (Exception ex) {
      span.end(ex);
    }
  }
}
