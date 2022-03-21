/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.junit.http;

import static io.opentelemetry.api.trace.SpanKind.SERVER;
import static io.opentelemetry.testing.internal.armeria.common.MediaType.PLAIN_TEXT_UTF_8;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.test.server.http.RequestContextGetter;
import io.opentelemetry.testing.internal.armeria.common.HttpData;
import io.opentelemetry.testing.internal.armeria.common.HttpResponse;
import io.opentelemetry.testing.internal.armeria.common.HttpStatus;
import io.opentelemetry.testing.internal.armeria.common.ResponseHeaders;
import io.opentelemetry.testing.internal.armeria.common.ResponseHeadersBuilder;
import io.opentelemetry.testing.internal.armeria.server.ServerBuilder;
import io.opentelemetry.testing.internal.armeria.server.logging.LoggingService;
import io.opentelemetry.testing.internal.armeria.testing.junit5.server.ServerExtension;
import java.io.FileInputStream;
import java.net.URI;
import java.security.KeyStore;
import java.time.Duration;
import javax.net.ssl.KeyManagerFactory;

public final class HttpClientTestServer extends ServerExtension {
  private static final String LONG_STRING;
  static {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 100_000; i++) {
      sb.append("1234567890");
    }
    LONG_STRING = sb.toString();
  }

  private final OpenTelemetry openTelemetry;
  private final Tracer tracer;

  public HttpClientTestServer(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
    tracer = openTelemetry.getTracer("test");
  }

  @Override
  protected void configure(ServerBuilder sb) throws Exception {
    KeyStore keystore = KeyStore.getInstance("PKCS12");
    keystore.load(
        new FileInputStream(System.getProperty("javax.net.ssl.trustStore")),
        "testing".toCharArray());
    KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    kmf.init(keystore, "testing".toCharArray());

    sb.http(0)
        .https(0)
        .tls(kmf)
        .service(
            "/success",
            (ctx, req) -> {
              ResponseHeadersBuilder headers = ResponseHeaders.builder(HttpStatus.OK);
              String testRequestId = req.headers().get("test-request-id");
              if (testRequestId != null) {
                headers.set("test-request-id", testRequestId);
              }
              // return HttpResponse.of(headers.build(), HttpData.ofAscii("Hello."));
              return HttpResponse.of(headers.build(), HttpData.ofAscii(LONG_STRING));
            })
        .service(
            "/client-error",
            (ctx, req) -> HttpResponse.of(HttpStatus.BAD_REQUEST, PLAIN_TEXT_UTF_8, "Invalid RQ"))
        .service(
            "/error",
            (ctx, req) ->
                HttpResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, PLAIN_TEXT_UTF_8, "Sorry."))
        .service("/redirect", (ctx, req) -> HttpResponse.ofRedirect(HttpStatus.FOUND, "/success"))
        .service(
            "/another-redirect",
            (ctx, req) -> HttpResponse.ofRedirect(HttpStatus.FOUND, "/redirect"))
        .service(
            "/circular-redirect",
            (ctx, req) -> HttpResponse.ofRedirect(HttpStatus.FOUND, "/circular-redirect"))
        .service(
            "/secured",
            (ctx, req) -> {
              String auth = req.headers().get(AbstractHttpClientTest.BASIC_AUTH_KEY);
              if (auth != null && auth.equals(AbstractHttpClientTest.BASIC_AUTH_VAL)) {
                return HttpResponse.of(
                    HttpStatus.OK, PLAIN_TEXT_UTF_8, "secured string under basic auth");
              }
              return HttpResponse.of(HttpStatus.UNAUTHORIZED, PLAIN_TEXT_UTF_8, "Unauthorized");
            })
        .service("/to-secured", (ctx, req) -> HttpResponse.ofRedirect(HttpStatus.FOUND, "/secured"))
        .service(
            "/read-timeout",
            (ctx, req) ->
                HttpResponse.delayed(HttpResponse.of(HttpStatus.OK), Duration.ofSeconds(20)))
        .decorator(
            (delegate, ctx, req) -> {
              for (String field : openTelemetry.getPropagators().getTextMapPropagator().fields()) {
                if (req.headers().getAll(field).size() > 1) {
                  throw new AssertionError((Object) ("more than one " + field + " header present"));
                }
              }
              SpanBuilder span =
                  tracer
                      .spanBuilder("test-http-server")
                      .setSpanKind(SERVER)
                      .setParent(
                          openTelemetry
                              .getPropagators()
                              .getTextMapPropagator()
                              .extract(Context.current(), ctx, RequestContextGetter.INSTANCE));

              String traceRequestId = req.headers().get("test-request-id");
              if (traceRequestId != null) {
                span.setAttribute("test.request.id", Integer.parseInt(traceRequestId));
              }
              span.startSpan().end();

              return delegate.serve(ctx, req);
            })
        .decorator(LoggingService.newDecorator());
  }

  public URI resolveAddress(String path) {
    return URI.create("http://localhost:" + httpPort() + path);
  }

  public URI resolveHttpsAddress(String path) {
    return URI.create("https://localhost:" + httpsPort() + path);
  }
}
