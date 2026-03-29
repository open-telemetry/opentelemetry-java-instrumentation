/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.httpurlconnection;

import static io.opentelemetry.instrumentation.api.internal.HttpConstants._OTHER;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD_ORIGINAL;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.semconv.http.HttpClientUrlTemplate;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import java.net.HttpURLConnection;
import java.util.Set;
import javax.annotation.Nullable;

public class HttpMethodAttributeExtractor<
        REQUEST extends HttpURLConnection, RESPONSE extends Integer>
    implements AttributesExtractor<REQUEST, RESPONSE> {

  private final Set<String> knownMethods;
  private final boolean emitExperimentalHttpClientTelemetry;

  private HttpMethodAttributeExtractor(Set<String> knownMethods) {
    this.knownMethods = knownMethods;
    emitExperimentalHttpClientTelemetry =
        AgentCommonConfig.get().shouldEmitExperimentalHttpClientTelemetry();
  }

  public static AttributesExtractor<? super HttpURLConnection, ? super Integer> create(
      Set<String> knownMethods) {
    return new HttpMethodAttributeExtractor<>(knownMethods);
  }

  @Override
  public void onStart(
      AttributesBuilder attributes, Context parentContext, HttpURLConnection connection) {}

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      HttpURLConnection connection,
      @Nullable Integer responseCode,
      @Nullable Throwable error) {

    GetOutputStreamContext getOutputStreamContext = GetOutputStreamContext.get(context);

    if (getOutputStreamContext.isOutputStreamMethodOfSunConnectionCalled()) {
      String method = connection.getRequestMethod();
      // The getOutputStream() has transformed "GET" into "POST"
      if (knownMethods.contains(method)) {
        attributes.put(HTTP_REQUEST_METHOD, method);
        attributes.remove(HTTP_REQUEST_METHOD_ORIGINAL);
      } else {
        attributes.put(HTTP_REQUEST_METHOD, _OTHER);
        attributes.put(HTTP_REQUEST_METHOD_ORIGINAL, method);
        method = "HTTP";
      }
      Span span = Span.fromContext(context);
      String urlTemplate =
          emitExperimentalHttpClientTelemetry ? HttpClientUrlTemplate.get(context) : null;
      span.updateName(method + (urlTemplate != null ? " " + urlTemplate : ""));
    }
  }
}
