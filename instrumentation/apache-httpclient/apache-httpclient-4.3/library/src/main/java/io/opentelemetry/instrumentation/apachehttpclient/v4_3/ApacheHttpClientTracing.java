package io.opentelemetry.instrumentation.apachehttpclient.v4_3;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;

public final class ApacheHttpClientTracing {

  public static ApacheHttpClientTracing create(OpenTelemetry openTelemetry) {
    return newBuilder(openTelemetry).build();
  }

  public static ApacheHttpClientTracingBuilder newBuilder(OpenTelemetry openTelemetry) {
    return new ApacheHttpClientTracingBuilder(openTelemetry);
  }

  private final Instrumenter<HttpUriRequest, HttpResponse> instrumenter;

  ApacheHttpClientTracing(Instrumenter<HttpUriRequest, HttpResponse> instrumenter) {
    this.instrumenter = instrumenter;
  }

  public CloseableHttpClient newHttpClient() {
    return newHttpClientBuilder().build();
  }

  public HttpClientBuilder newHttpClientBuilder() {
    return new TracingHttpClientBuilder(instrumenter);
  }
}
