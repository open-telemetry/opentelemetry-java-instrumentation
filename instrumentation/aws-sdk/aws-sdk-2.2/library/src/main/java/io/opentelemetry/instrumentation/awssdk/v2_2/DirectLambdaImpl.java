/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.GlobalOpenTelemetry;
import software.amazon.awssdk.core.SdkRequest;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

// this class is only used from DirectLambdaAccess from method with @NoMuzzle annotation

// Direct lambda invocations (e.g., not through an api gateway) currently strip
// away the otel propagation headers (but leave x-ray ones intact).  Use the
// custom client context header as an additional propagation mechanism for this
// very specific scenario.  For reference, the header is named "X-Amz-Client-Context" but the api to manipulate
// it abstracts that away.  The client context field is documented in https://docs.aws.amazon.com/lambda/latest/api/API_Invoke.html#API_Invoke_RequestParameters

final class DirectLambdaImpl {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  public static final String CLIENT_CONTEXT_CUSTOM_FIELDS_KEY = "custom";
  public static final int MAX_CLIENT_CONTEXT_LENGTH = 3583;

  private DirectLambdaImpl() {}

  @Nullable
  static SdkRequest modifyRequest(
      SdkRequest request,
      io.opentelemetry.context.Context otelContext) {
    if (isDirectLambdaInvocation(request)) {
      try {
        return modifyOrAddCustomContextHeader((InvokeRequest) request, otelContext);
      } catch (Exception e) {
        return null;
      }

    }
    return null;
  }
  static boolean isDirectLambdaInvocation(SdkRequest request) {
    return request instanceof InvokeRequest;
  }

  @SuppressWarnings("unchecked")
  static SdkRequest modifyOrAddCustomContextHeader(
      InvokeRequest request,
      io.opentelemetry.context.Context otelContext) throws Exception{
    InvokeRequest.Builder builder = request.toBuilder();
    // Unfortunately the value of this thing is a base64-encoded json with a character limit; also
    // therefore not comma-composable like many http headers
    String clientContextString = request.clientContext();
    String clientContextJsonString = "{}";
    if (clientContextString != null && !clientContextString.isEmpty()) {
      clientContextJsonString = new String(Base64.getDecoder().decode(clientContextString), StandardCharsets.UTF_8);
    }
    Map<String,Object> parsedJson = (Map<String, Object>) OBJECT_MAPPER.readValue(clientContextJsonString, new TypeReference<Object>() {});
    Map<String, Object> customFields = (Map<String,Object>) parsedJson.getOrDefault(
        CLIENT_CONTEXT_CUSTOM_FIELDS_KEY, new HashMap<String, Object>());

    int numCustomFields = customFields.size();
    GlobalOpenTelemetry.getPropagators().getTextMapPropagator().inject(otelContext, customFields, Map::put);
    if (numCustomFields == customFields.size()) {
      return null; // no modifications made
    }

    System.out.println("JBLEY addHeader added headers: "+customFields);

    parsedJson.put(CLIENT_CONTEXT_CUSTOM_FIELDS_KEY, customFields);

    // turn it back into a string (json encode)
    String newJson = OBJECT_MAPPER.writeValueAsString(parsedJson);
    System.out.println("JBLEY addHeader newJson: "+newJson);
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
