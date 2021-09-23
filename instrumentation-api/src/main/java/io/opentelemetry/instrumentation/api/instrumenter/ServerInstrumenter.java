/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.internal.ContextPropagationDebug;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.checkerframework.checker.nullness.qual.Nullable;

final class ServerInstrumenter<REQUEST, RESPONSE> extends Instrumenter<REQUEST, RESPONSE> {

  private final ContextPropagators propagators;
  private final TextMapGetter<REQUEST> getter;

  ServerInstrumenter(
      InstrumenterBuilder<REQUEST, RESPONSE> builder, TextMapGetter<REQUEST> getter) {
    super(addClientIpExtractor(builder, getter));
    this.propagators = builder.openTelemetry.getPropagators();
    this.getter = getter;
  }

  @Override
  public Context start(Context parentContext, REQUEST request) {
    ContextPropagationDebug.debugContextLeakIfEnabled();

    Context extracted = propagators.getTextMapPropagator().extract(parentContext, request, getter);
    return super.start(extracted, request);
  }

  private static <REQUEST, RESPONSE> InstrumenterBuilder<REQUEST, RESPONSE> addClientIpExtractor(
      InstrumenterBuilder<REQUEST, RESPONSE> builder, TextMapGetter<REQUEST> getter) {
    HttpServerAttributesExtractor<REQUEST, RESPONSE> httpAttributesExtractor = null;
    for (AttributesExtractor<? super REQUEST, ? super RESPONSE> extractor :
        builder.attributesExtractors) {
      if (extractor instanceof HttpServerAttributesExtractor) {
        httpAttributesExtractor = (HttpServerAttributesExtractor<REQUEST, RESPONSE>) extractor;
      }
    }
    if (httpAttributesExtractor == null) {
      // Don't add HTTP_CLIENT_IP if there are no HTTP attributes registered.
      return builder;
    }
    builder.addAttributesExtractor(new HttpClientIpExtractor<>(getter));
    return builder;
  }

  private static class HttpClientIpExtractor<REQUEST, RESPONSE>
      extends AttributesExtractor<REQUEST, RESPONSE> {

    private final TextMapGetter<REQUEST> getter;

    HttpClientIpExtractor(TextMapGetter<REQUEST> getter) {
      this.getter = getter;
    }

    @Override
    protected void onStart(AttributesBuilder attributes, REQUEST request) {}

    @Override
    protected void onEnd(
        AttributesBuilder attributes,
        REQUEST request,
        @Nullable RESPONSE response,
        @Nullable Throwable error) {
      String clientIp = getForwardedClientIp(request);
      set(attributes, SemanticAttributes.HTTP_CLIENT_IP, clientIp);
    }

    @Nullable
    // Visible for testing
    String getForwardedClientIp(REQUEST request) {
      // try Forwarded
      String forwarded = getter.get(request, "Forwarded");
      if (forwarded != null) {
        forwarded = extractForwarded(forwarded);
        if (forwarded != null) {
          return forwarded;
        }
      }

      // try X-Forwarded-For
      forwarded = getter.get(request, "X-Forwarded-For");
      if (forwarded != null) {
        return extractForwardedFor(forwarded);
      }

      return null;
    }
  }

  // VisibleForTesting
  @Nullable
  static String extractForwarded(String forwarded) {
    int start = forwarded.toLowerCase().indexOf("for=");
    if (start < 0) {
      return null;
    }
    start += 4; // start is now the index after for=
    if (start >= forwarded.length() - 1) { // the value after for= must not be empty
      return null;
    }
    return extractIpAddress(forwarded, start);
  }

  // VisibleForTesting
  @Nullable
  static String extractForwardedFor(String forwarded) {
    return extractIpAddress(forwarded, 0);
  }

  // from https://www.rfc-editor.org/rfc/rfc7239
  //  "Note that IPv6 addresses may not be quoted in
  //   X-Forwarded-For and may not be enclosed by square brackets, but they
  //   are quoted and enclosed in square brackets in Forwarded"
  // and also (applying to Forwarded but not X-Forwarded-For)
  //  "It is important to note that an IPv6 address and any nodename with
  //   node-port specified MUST be quoted, since ':' is not an allowed
  //   character in 'token'."
  @Nullable
  private static String extractIpAddress(String forwarded, int start) {
    if (forwarded.length() == start) {
      return null;
    }
    if (forwarded.charAt(start) == '"') {
      return extractIpAddress(forwarded, start + 1);
    }
    if (forwarded.charAt(start) == '[') {
      int end = forwarded.indexOf(']', start + 1);
      if (end == -1) {
        return null;
      }
      return forwarded.substring(start + 1, end);
    }
    boolean inIpv4 = false;
    for (int i = start; i < forwarded.length() - 1; i++) {
      char c = forwarded.charAt(i);
      if (c == '.') {
        inIpv4 = true;
      } else if (c == ',' || c == ';' || c == '"' || (inIpv4 && c == ':')) {
        if (i == start) { // empty string
          return null;
        }
        return forwarded.substring(start, i);
      }
    }
    return forwarded.substring(start);
  }
}
