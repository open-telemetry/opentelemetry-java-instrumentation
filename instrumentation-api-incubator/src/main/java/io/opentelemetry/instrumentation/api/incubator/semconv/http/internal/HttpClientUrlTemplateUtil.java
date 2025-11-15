/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.http.internal;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.semconv.http.HttpClientExperimentalAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.http.HttpClientUrlTemplate;
import io.opentelemetry.instrumentation.api.incubator.semconv.http.HttpClientUrlTemplateCustomizer;
import io.opentelemetry.instrumentation.api.internal.InstrumenterContext;
import io.opentelemetry.instrumentation.api.internal.ServiceLoaderUtil;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesGetter;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class HttpClientUrlTemplateUtil {

  private static final List<HttpClientUrlTemplateCustomizer> customizers = new ArrayList<>();

  static {
    for (HttpClientUrlTemplateCustomizer customizer :
        ServiceLoaderUtil.load(HttpClientUrlTemplateCustomizer.class)) {
      customizers.add(customizer);
    }
  }

  @Nullable
  public static <REQUEST> String getUrlTemplate(
      Context context, REQUEST request, HttpClientAttributesGetter<REQUEST, ?> getter) {
    // first, try to get url template from context
    String urlTemplate = HttpClientUrlTemplate.get(context);
    if (urlTemplate == null && getter instanceof HttpClientExperimentalAttributesGetter) {
      HttpClientExperimentalAttributesGetter<REQUEST, ?> experimentalGetter =
          (HttpClientExperimentalAttributesGetter<REQUEST, ?>) getter;
      // next, try to get url template from getter
      urlTemplate = experimentalGetter.getUrlTemplate(request);
    }

    return customizeUrlTemplate(urlTemplate, request, getter);
  }

  @Nullable
  private static <REQUEST> String customizeUrlTemplate(
      @Nullable String urlTemplate,
      REQUEST request,
      HttpClientAttributesGetter<REQUEST, ?> getter) {
    if (customizers.isEmpty()) {
      return urlTemplate;
    }

    // we cache the computation in InstrumenterContext because url template is used by both
    // HttpSpanNameExtractor and HttpExperimentalAttributesExtractor
    return InstrumenterContext.computeIfAbsent(
        "url.template",
        unused -> {
          for (HttpClientUrlTemplateCustomizer customizer : customizers) {
            String result = customizer.customize(urlTemplate, request, getter);
            if (result != null) {
              return result;
            }
          }

          return urlTemplate;
        });
  }

  private HttpClientUrlTemplateUtil() {}
}
