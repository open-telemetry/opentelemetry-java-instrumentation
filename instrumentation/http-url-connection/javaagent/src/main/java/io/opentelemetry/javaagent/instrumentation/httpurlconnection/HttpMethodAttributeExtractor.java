/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.httpurlconnection;

import static io.opentelemetry.instrumentation.api.internal.AttributesExtractorUtil.internalSet;
import static io.opentelemetry.instrumentation.api.internal.HttpConstants._OTHER;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.semconv.HttpAttributes;
import java.net.HttpURLConnection;
import java.util.Set;
import javax.annotation.Nullable;

public class HttpMethodAttributeExtractor<
        REQUEST extends HttpURLConnection, RESPONSE extends Integer>
    implements AttributesExtractor<REQUEST, RESPONSE> {

  private final Set<String> knownMethods;

  private HttpMethodAttributeExtractor(Set<String> knownMethods) {
    this.knownMethods = knownMethods;
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
        internalSet(attributes, HttpAttributes.HTTP_REQUEST_METHOD, method);
        attributes.remove(HttpAttributes.HTTP_REQUEST_METHOD_ORIGINAL);
      } else {
        internalSet(attributes, HttpAttributes.HTTP_REQUEST_METHOD, _OTHER);
        internalSet(attributes, HttpAttributes.HTTP_REQUEST_METHOD_ORIGINAL, method);
      }
      Span span = Span.fromContext(context);
      span.updateName(method);
    }
  }
}
