package io.opentelemetry.instrumentation.api.instrumenter.http;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

import io.opentelemetry.api.common.AttributeKey;

public final class SemanticAttributes {

  public static final AttributeKey<String> HTTP_REQUEST_BODY = stringKey("http.request.body");
  public static final AttributeKey<String> HTTP_REQUEST_HEADERS = stringKey("http.request.headers");
  public static final AttributeKey<String> HTTP_RESPONSE_BODY = stringKey("http.response.body");
  public static final AttributeKey<String> HTTP_RESPONSE_HEADERS = stringKey("http.response.headers");

}
