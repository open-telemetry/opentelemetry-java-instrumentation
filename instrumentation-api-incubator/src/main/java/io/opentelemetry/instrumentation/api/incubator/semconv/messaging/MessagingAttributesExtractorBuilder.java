/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.messaging;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.internal.DeprecatedCaptureNames;
import java.util.Collection;
import javax.annotation.Nullable;

/** A builder of {@link MessagingAttributesExtractor}. */
public final class MessagingAttributesExtractorBuilder<REQUEST, RESPONSE> {

  final MessagingAttributesGetter<REQUEST, RESPONSE> getter;
  @Nullable private final MessagingOperationType operationType;
  @Nullable private final String operationName;
  private final boolean supportsStableSemconv;
  @Nullable IncludeExclude headers;

  MessagingAttributesExtractorBuilder(
      MessagingAttributesGetter<REQUEST, RESPONSE> getter,
      @Nullable MessagingOperationType operationType,
      @Nullable String operationName,
      boolean supportsStableSemconv) {
    this.getter = getter;
    this.operationType = operationType;
    this.operationName = operationName;
    this.supportsStableSemconv = supportsStableSemconv;
  }

  /**
   * Configures which message headers are captured as span attributes.
   *
   * <p>Header values are captured under the {@code messaging.header.<name>} attribute key. The
   * {@code <name>} part in the attribute key is the header name with dashes replaced by underscores
   * unless {@code otel.instrumentation.common.v3-preview} is enabled, in which case dashes are
   * preserved.
   *
   * <p>Selector patterns are matched case-sensitively. {@code ?} matches one character and {@code
   * *} matches any number of characters, including none. Excluded patterns take precedence over
   * included patterns. A selector with no included patterns captures every header that is not
   * excluded, and an {@linkplain IncludeExclude#isEmpty() empty} selector captures no headers.
   * Exact included names are looked up directly, so those lookups follow the underlying messaging
   * library's header-name case sensitivity.
   *
   * <p>Header names that are not listed as exact included names are resolved through {@link
   * MessagingAttributesGetter#getMessageHeaderNames(Object)}, so wildcard and exclude-only
   * selectors only capture headers when the getter implements that method.
   */
  @CanIgnoreReturnValue
  public MessagingAttributesExtractorBuilder<REQUEST, RESPONSE> setHeaders(IncludeExclude headers) {
    this.headers = headers.isEmpty() ? null : headers;
    return this;
  }

  /**
   * Configures the messaging headers that will be captured as span attributes.
   *
   * <p>The messaging header values will be captured under the {@code messaging.header.<name>}
   * attribute key. The {@code <name>} part in the attribute key is the header name with dashes
   * replaced by underscores unless {@code otel.instrumentation.common.v3-preview} is enabled, in
   * which case dashes are preserved.
   *
   * <p>The header names are matched literally. Names containing {@code *} or {@code ?} are ignored
   * and logged, since this setting never supported wildcards.
   *
   * @param capturedHeaders A list of messaging header names.
   * @deprecated Use {@link #setHeaders(IncludeExclude)} instead. May be removed in the next minor
   *     release.
   */
  @Deprecated // may be removed in the next minor release
  @CanIgnoreReturnValue
  public MessagingAttributesExtractorBuilder<REQUEST, RESPONSE> setCapturedHeaders(
      Collection<String> capturedHeaders) {
    return setHeaders(
        DeprecatedCaptureNames.toSelectorOrEmpty(
            capturedHeaders,
            "MessagingAttributesExtractorBuilder.setCapturedHeaders()",
            "setHeaders(IncludeExclude)"));
  }

  /**
   * Returns a new {@link MessagingAttributesExtractor} with the settings of this {@link
   * MessagingAttributesExtractorBuilder}.
   */
  public AttributesExtractor<REQUEST, RESPONSE> build() {
    return new MessagingAttributesExtractor<>(
        getter, operationType, operationName, supportsStableSemconv, headers);
  }
}
