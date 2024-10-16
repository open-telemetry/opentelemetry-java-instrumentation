/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

import io.opentelemetry.api.GlobalOpenTelemetry;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nullable;
import software.amazon.awssdk.core.SdkRequest;
import software.amazon.awssdk.protocols.jsoncore.JsonNode;
import software.amazon.awssdk.protocols.jsoncore.internal.ObjectJsonNode;
import software.amazon.awssdk.protocols.jsoncore.internal.StringJsonNode;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;

// this class is only used from LambdaAccess from method with @NoMuzzle annotation

// Direct lambda invocations (e.g., not through an api gateway) currently strip
// away the otel propagation headers (but leave x-ray ones intact). Use the
// custom client context header as an additional propagation mechanism for this
// very specific scenario. For reference, the header is named "X-Amz-Client-Context" but the api to
// manipulate it abstracts that away. The client context field is documented in
// https://docs.aws.amazon.com/lambda/latest/api/API_Invoke.html#API_Invoke_RequestParameters

final class LambdaImpl {
  static {
    // Force loading of InvokeRequest; this ensures that an exception is thrown at this point when
    // the Lambda library is not present, which will cause DirectLambdaAccess to have
    // enabled=false in library mode.
    @SuppressWarnings("unused")
    String invokeRequestName = InvokeRequest.class.getName();
    // was added in 2.17.0
    @SuppressWarnings("unused")
    String jsonNodeName = JsonNode.class.getName();
  }

  private static final String CLIENT_CONTEXT_CUSTOM_FIELDS_KEY = "custom";
  static final int MAX_CLIENT_CONTEXT_LENGTH = 3583; // visible for testing

  private LambdaImpl() {}

  @Nullable
  static SdkRequest modifyRequest(
      SdkRequest request, io.opentelemetry.context.Context otelContext) {
    if (isDirectLambdaInvocation(request)) {
      return modifyOrAddCustomContextHeader((InvokeRequest) request, otelContext);
    }
    return null;
  }

  static boolean isDirectLambdaInvocation(SdkRequest request) {
    return request instanceof InvokeRequest;
  }

  static SdkRequest modifyOrAddCustomContextHeader(
      InvokeRequest request, io.opentelemetry.context.Context otelContext) {
    InvokeRequest.Builder builder = request.toBuilder();
    // Unfortunately the value of this thing is a base64-encoded json with a character limit; also
    // therefore not comma-composable like many http headers
    String clientContextString = request.clientContext();
    String clientContextJsonString = "{}";
    if (clientContextString != null && !clientContextString.isEmpty()) {
      clientContextJsonString =
          new String(Base64.getDecoder().decode(clientContextString), StandardCharsets.UTF_8);
    }
    JsonNode jsonNode = JsonNode.parser().parse(clientContextJsonString);
    if (!jsonNode.isObject()) {
      return null;
    }
    JsonNode customNode =
        jsonNode
            .asObject()
            .computeIfAbsent(
                CLIENT_CONTEXT_CUSTOM_FIELDS_KEY, (k) -> new ObjectJsonNode(new LinkedHashMap<>()));
    if (!customNode.isObject()) {
      return null;
    }
    Map<String, JsonNode> map = customNode.asObject();
    GlobalOpenTelemetry.getPropagators()
        .getTextMapPropagator()
        .inject(otelContext, map, (nodes, key, value) -> nodes.put(key, new StringJsonNode(value)));
    if (map.isEmpty()) {
      return null;
    }

    // turn it back into a string (json encode)
    String newJson = jsonNode.toString();

    // turn it back into a base64 string
    String newJson64 = Base64.getEncoder().encodeToString(newJson.getBytes(StandardCharsets.UTF_8));
    // check it for length (err on the safe side with >=)
    if (newJson64.length() >= MAX_CLIENT_CONTEXT_LENGTH) {
      return null;
    }
    builder.clientContext(newJson64);
    return builder.build();
  }
}
