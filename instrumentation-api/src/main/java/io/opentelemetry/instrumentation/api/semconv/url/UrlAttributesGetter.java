/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.semconv.url;

import javax.annotation.Nullable;

/**
 * An interface for getting URL attributes.
 *
 * <p>Instrumentation authors will create implementations of this interface for their specific
 * library/framework. It will be used by the {@link UrlAttributesExtractor} (or other convention
 * specific extractors) to obtain the various URL attributes in a type-generic way.
 *
 * @since 2.0.0
 */
public interface UrlAttributesGetter<REQUEST> {

  /**
   * Returns the <a href="https://www.rfc-editor.org/rfc/rfc3986#section-3.1">URI scheme</a>
   * component identifying the used protocol.
   *
   * <p>Examples: {@code https}, {@code ftp}, {@code telnet}
   */
  @Nullable
  default String getUrlScheme(REQUEST request) {
    return null;
  }

  /**
   * Returns the <a href="https://www.rfc-editor.org/rfc/rfc3986#section-3.3">URI path</a>
   * component.
   *
   * <p>Examples: {@code /search}
   */
  @Nullable
  default String getUrlPath(REQUEST request) {
    return null;
  }

  /**
   * Returns the <a href="https://www.rfc-editor.org/rfc/rfc3986#section-3.4">URI query</a>
   * component.
   *
   * <p>Examples: {@code q=OpenTelemetry}
   */
  @Nullable
  default String getUrlQuery(REQUEST request) {
    return null;
  }
}
