/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdaevents.v2_2.internal;

import static io.opentelemetry.instrumentation.api.internal.AttributesExtractorUtil.internalSet;
import static io.opentelemetry.instrumentation.api.internal.HttpConstants._OTHER;
import static io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.MapUtils.emptyIfNull;
import static io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.MapUtils.lowercaseMap;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.UserAgentAttributes.USER_AGENT_ORIGINAL;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.AwsLambdaRequest;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.UrlAttributes;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

final class ApiGatewayProxyAttributesExtractor
    implements AttributesExtractor<AwsLambdaRequest, Object> {

  // copied from FaasIncubatingAttributes
  private static final AttributeKey<String> FAAS_TRIGGER = AttributeKey.stringKey("faas.trigger");
  // copied from FaasIncubatingAttributes.FaasTriggerValues
  private static final String HTTP = "http";

  private final Set<String> knownMethods;

  ApiGatewayProxyAttributesExtractor(Set<String> knownMethods) {
    this.knownMethods = knownMethods;
  }

  @Override
  public void onStart(
      AttributesBuilder attributes, Context parentContext, AwsLambdaRequest request) {
    if (request.getInput() instanceof APIGatewayProxyRequestEvent) {
      attributes.put(FAAS_TRIGGER, HTTP);
      onRequest(attributes, (APIGatewayProxyRequestEvent) request.getInput());
    }
  }

  void onRequest(AttributesBuilder attributes, APIGatewayProxyRequestEvent request) {
    String method = request.getHttpMethod();
    if (method == null || knownMethods.contains(method)) {
      internalSet(attributes, HttpAttributes.HTTP_REQUEST_METHOD, method);
    } else {
      internalSet(attributes, HttpAttributes.HTTP_REQUEST_METHOD, _OTHER);
      internalSet(attributes, HttpAttributes.HTTP_REQUEST_METHOD_ORIGINAL, method);
    }

    Map<String, String> headers = lowercaseMap(request.getHeaders());
    String userAgent = headers.get("user-agent");
    if (userAgent != null) {
      attributes.put(USER_AGENT_ORIGINAL, userAgent);
    }

    internalSet(attributes, UrlAttributes.URL_FULL, getHttpUrl(request, headers));
  }

  private static String getHttpUrl(
      APIGatewayProxyRequestEvent request, Map<String, String> headers) {
    StringBuilder str = new StringBuilder();

    String scheme = headers.get("x-forwarded-proto");
    if (scheme != null) {
      str.append(scheme).append("://");
    }
    String host = headers.get("host");
    if (host != null) {
      str.append(host);
    }
    String path = request.getPath();
    if (path != null) {
      str.append(path);
    }

    try {
      boolean first = true;
      for (Map.Entry<String, String> entry :
          emptyIfNull(request.getQueryStringParameters()).entrySet()) {
        String key = URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8.name());
        String value = URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8.name());
        str.append(first ? '?' : '&').append(key).append('=').append(value);
        first = false;
      }
    } catch (UnsupportedEncodingException ignored) {
      // Ignore
    }
    return str.length() == 0 ? null : str.toString();
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      AwsLambdaRequest request,
      @Nullable Object response,
      @Nullable Throwable error) {
    if (response instanceof APIGatewayProxyResponseEvent) {
      Integer statusCode = ((APIGatewayProxyResponseEvent) response).getStatusCode();
      if (statusCode != null) {
        attributes.put(HTTP_RESPONSE_STATUS_CODE, statusCode);
      }
    }
  }
}
