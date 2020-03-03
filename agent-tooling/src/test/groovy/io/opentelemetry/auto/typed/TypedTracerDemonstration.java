package io.opentelemetry.auto.typed;

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

    try (Scope scope = tracer.withSpan(span)) {
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

    try (Scope scope = tracer.withSpan(span)) {
      // make request
      String response = "response instance";

      span.end(response);
      // span.onResponse("response instance"); // implicitly called on end.
    } catch (Exception ex) {
      span.end(ex);
    }
  }
}
