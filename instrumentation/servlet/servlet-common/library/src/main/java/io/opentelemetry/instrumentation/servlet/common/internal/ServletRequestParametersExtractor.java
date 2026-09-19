/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.common.internal;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class ServletRequestParametersExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<
        ServletRequestContext<REQUEST>, ServletResponseContext<RESPONSE>> {

  private final ServletAccessor<REQUEST, RESPONSE> accessor;
  private final IncludeExclude requestParameters;
  private final Map<String, AttributeKey<List<String>>> literalParameterKeys;

  public ServletRequestParametersExtractor(
      ServletAccessor<REQUEST, RESPONSE> accessor, IncludeExclude requestParameters) {
    this.accessor = accessor;
    this.requestParameters = requestParameters;
    this.literalParameterKeys = createLiteralParameterKeys(requestParameters);
  }

  public void setAttributes(
      REQUEST request, BiConsumer<AttributeKey<List<String>>, List<String>> consumer) {
    if (requestParameters.isEmpty()) {
      return;
    }
    for (String name : accessor.getRequestParameterNames(request)) {
      if (!requestParameters.matches(name)) {
        continue;
      }
      List<String> values = accessor.getRequestParameterValues(request, name);
      if (!values.isEmpty()) {
        consumer.accept(parameterAttributeKey(name), values);
      }
    }
  }

  @Override
  public void onStart(
      AttributesBuilder attributes,
      Context parentContext,
      ServletRequestContext<REQUEST> requestContext) {}

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      ServletRequestContext<REQUEST> requestContext,
      @Nullable ServletResponseContext<RESPONSE> responseContext,
      @Nullable Throwable error) {
    // request parameters are extracted at the end of the request to make sure that we don't access
    // them before request encoding has been set
    REQUEST request = requestContext.request();
    setAttributes(request, attributes::put);
  }

  private AttributeKey<List<String>> parameterAttributeKey(String parameterName) {
    AttributeKey<List<String>> key = literalParameterKeys.get(parameterName);
    return key != null ? key : createKey(parameterName);
  }

  private static Map<String, AttributeKey<List<String>>> createLiteralParameterKeys(
      IncludeExclude selector) {
    Map<String, AttributeKey<List<String>>> result = new HashMap<>();
    for (String pattern : selector.getIncluded()) {
      if (pattern.indexOf('*') == -1 && pattern.indexOf('?') == -1) {
        result.put(pattern, createKey(pattern));
      }
    }
    return result;
  }

  private static AttributeKey<List<String>> createKey(String parameterName) {
    if (!SemconvStability.v3Preview()) {
      // normalize parameter name similarly as is done with header names when header values are
      // captured as span attributes
      parameterName = parameterName.toLowerCase(Locale.ROOT);
    }
    String key = "servlet.request.parameter." + parameterName;
    return AttributeKey.stringArrayKey(key);
  }
}
