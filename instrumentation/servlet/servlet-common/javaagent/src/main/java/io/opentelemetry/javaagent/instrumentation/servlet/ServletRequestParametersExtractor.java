/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.servlet.ServletAccessor;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;

public class ServletRequestParametersExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<
        ServletRequestContext<REQUEST>, ServletResponseContext<RESPONSE>> {
  private static final List<String> CAPTURE_REQUEST_PARAMETERS =
      Config.get().getList("otel.instrumentation.servlet.experimental.capture-request-parameters");

  private static final ConcurrentMap<String, AttributeKey<List<String>>> parameterKeysCache =
      new ConcurrentHashMap<>();

  private final ServletAccessor<REQUEST, RESPONSE> accessor;

  public ServletRequestParametersExtractor(ServletAccessor<REQUEST, RESPONSE> accessor) {
    this.accessor = accessor;
  }

  public static boolean enabled() {
    return !CAPTURE_REQUEST_PARAMETERS.isEmpty();
  }

  public void setAttributes(
      REQUEST request, BiConsumer<AttributeKey<List<String>>, List<String>> consumer) {
    for (String name : CAPTURE_REQUEST_PARAMETERS) {
      List<String> values = accessor.getRequestParameterValues(request, name);
      if (!values.isEmpty()) {
        consumer.accept(parameterAttributeKey(name), values);
      }
    }
  }

  @Override
  public void onStart(AttributesBuilder attributes, ServletRequestContext<REQUEST> requestContext) {
    REQUEST request = requestContext.request();
    setAttributes(request, (key, value) -> set(attributes, key, value));
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      ServletRequestContext<REQUEST> requestContext,
      @Nullable ServletResponseContext<RESPONSE> responseContext,
      @Nullable Throwable error) {}

  private static AttributeKey<List<String>> parameterAttributeKey(String headerName) {
    return parameterKeysCache.computeIfAbsent(headerName, n -> createKey(n));
  }

  private static AttributeKey<List<String>> createKey(String parameterName) {
    // normalize parameter name similarly as is done with header names when header values are
    // captured as span attributes
    parameterName = parameterName.toLowerCase(Locale.ROOT);
    String key = "servlet.request.parameter." + parameterName.replace('-', '_');
    return AttributeKey.stringArrayKey(key);
  }
}
