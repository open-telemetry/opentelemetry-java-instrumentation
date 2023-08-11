/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet;

import static java.util.Collections.emptyList;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.javaagent.bootstrap.internal.InstrumentationConfig;
import java.util.List;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;

public class ServletRequestParametersExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<
        ServletRequestContext<REQUEST>, ServletResponseContext<RESPONSE>> {
  private static final List<String> CAPTURE_REQUEST_PARAMETERS =
      InstrumentationConfig.get()
          .getList(
              "otel.instrumentation.servlet.experimental.capture-request-parameters", emptyList());

  private final ServletAccessor<REQUEST, RESPONSE> accessor;

  public ServletRequestParametersExtractor(ServletAccessor<REQUEST, RESPONSE> accessor) {
    this.accessor = accessor;
  }

  public static boolean enabled() {
    return !CAPTURE_REQUEST_PARAMETERS.isEmpty();
  }

  private static boolean captureAll() {
    return CAPTURE_REQUEST_PARAMETERS.size() == 1 && CAPTURE_REQUEST_PARAMETERS.get(0).equals("*");
  }

  public void setAttributes(
      REQUEST request, BiConsumer<AttributeKey<List<String>>, List<String>> consumer) {
    if (captureAll()) {
      accessor.forAllQueryParams(request, consumer);
      return;
    }
    for (String name : CAPTURE_REQUEST_PARAMETERS) {
      List<String> values = accessor.getRequestParameterValues(request, name);
      if (!values.isEmpty()) {
        consumer.accept(QueryParameterKeyCache.get(name), values);
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
}
