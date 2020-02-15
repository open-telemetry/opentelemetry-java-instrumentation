package io.opentelemetry.helpers.apachehttpclient;

import io.opentelemetry.context.propagation.HttpTextFormat.Setter;
import io.opentelemetry.distributedcontext.DistributedContextManager;
import io.opentelemetry.helpers.core.HttpClientSpanDecorator;
import io.opentelemetry.metrics.Meter;
import io.opentelemetry.trace.Tracer;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpUriRequest;

public class ApacheHttpClientSpanDecorator
    extends HttpClientSpanDecorator<HttpUriRequest, HttpUriRequest, HttpResponse> {

  static final ApacheHttpClientExtractor EXTRACTOR = new ApacheHttpClientExtractor();
  static final Setter<HttpUriRequest> SETTER =
      new Setter<HttpUriRequest>() {
        @Override
        public void put(HttpUriRequest carrier, String key, String value) {
          carrier.addHeader(key, value);
        }
      };

  /**
   * Constructs a span decorator object.
   *
   * @param tracer the tracer to use in recording spans
   * @param contextManager the context manager to use in handling correlation contexts
   * @param meter the meter to use in recoding measurements
   */
  public ApacheHttpClientSpanDecorator(
      Tracer tracer, DistributedContextManager contextManager, Meter meter) {
    super(tracer, contextManager, meter, SETTER, EXTRACTOR);
  }
}
