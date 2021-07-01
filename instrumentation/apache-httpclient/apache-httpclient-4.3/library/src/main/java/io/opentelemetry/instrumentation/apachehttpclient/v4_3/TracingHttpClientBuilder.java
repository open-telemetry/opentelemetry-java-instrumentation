package io.opentelemetry.instrumentation.apachehttpclient.v4_3;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.execchain.ClientExecChain;

final class TracingHttpClientBuilder extends HttpClientBuilder {

  private final Instrumenter<HttpUriRequest, HttpResponse> instrumenter;

  TracingHttpClientBuilder(
      Instrumenter<HttpUriRequest, HttpResponse> instrumenter) {this.instrumenter = instrumenter;}


  @Override
  protected ClientExecChain decorateProtocolExec(ClientExecChain protocolExec) {
    return new TracingProtocolExec(instrumenter, propagators, protocolExec);
  }
}
