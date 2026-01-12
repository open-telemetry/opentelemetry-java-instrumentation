/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.http;

import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesGetter;
import javax.annotation.Nullable;

/** A service provider interface (SPI) for customizing http client url template. */
public interface HttpClientUrlTemplateCustomizer {

  /**
   * Customize url template for given request. Typically, the customizer will extract full url from
   * the request and apply some logic (e.g. regex matching) to generate url template. The customizer
   * can choose to override existing url template or skip customization when a url template is
   * already set.
   *
   * @param urlTemplate existing url template, can be null
   * @param request current request
   * @param getter request attributes getter
   * @return customized url template, or null
   */
  @Nullable
  <REQUEST> String customize(
      @Nullable String urlTemplate, REQUEST request, HttpClientAttributesGetter<REQUEST, ?> getter);
}
