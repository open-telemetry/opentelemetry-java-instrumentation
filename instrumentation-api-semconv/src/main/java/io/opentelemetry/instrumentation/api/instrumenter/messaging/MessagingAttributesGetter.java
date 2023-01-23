/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.messaging;

import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;

/**
 * An interface for getting messaging attributes.
 *
 * <p>Instrumentation authors will create implementations of this interface for their specific
 * library/framework. It will be used by the {@link MessagingAttributesExtractor} to obtain the
 * various messaging attributes in a type-generic way.
 */
public interface MessagingAttributesGetter<REQUEST, RESPONSE> {

  @Nullable
  default String getSystem(REQUEST request) {
    return system(request);
  }

  /**
   * This method is deprecated and will be removed in the subsequent release.
   *
   * @deprecated Use {@link #getSystem(Object)} instead.
   */
  @Deprecated
  @Nullable
  default String system(REQUEST request) {
    throw new UnsupportedOperationException(
        "This method is deprecated and will be removed in the subsequent release.");
  }

  @Nullable
  default String getDestinationKind(REQUEST request) {
    return destinationKind(request);
  }

  /**
   * This method is deprecated and will be removed in the subsequent release.
   *
   * @deprecated Use {@link #getDestinationKind(Object)} instead.
   */
  @Deprecated
  @Nullable
  default String destinationKind(REQUEST request) {
    throw new UnsupportedOperationException(
        "This method is deprecated and will be removed in the subsequent release.");
  }

  @Nullable
  default String getDestination(REQUEST request) {
    return destination(request);
  }

  /**
   * This method is deprecated and will be removed in the subsequent release.
   *
   * @deprecated Use {@link #getDestination(Object)} instead.
   */
  @Deprecated
  @Nullable
  default String destination(REQUEST request) {
    throw new UnsupportedOperationException(
        "This method is deprecated and will be removed in the subsequent release.");
  }

  default boolean isTemporaryDestination(REQUEST request) {
    return temporaryDestination(request);
  }

  /**
   * This method is deprecated and will be removed in the subsequent release.
   *
   * @deprecated Use {@link #isTemporaryDestination(Object)} instead.
   */
  @Deprecated
  default boolean temporaryDestination(REQUEST request) {
    throw new UnsupportedOperationException(
        "This method is deprecated and will be removed in the subsequent release.");
  }

  @Nullable
  default String getProtocol(REQUEST request) {
    return protocol(request);
  }

  /**
   * This method is deprecated and will be removed in the subsequent release.
   *
   * @deprecated Use {@link #getProtocol(Object)} instead.
   */
  @Deprecated
  @Nullable
  default String protocol(REQUEST request) {
    throw new UnsupportedOperationException(
        "This method is deprecated and will be removed in the subsequent release.");
  }

  @Nullable
  default String getProtocolVersion(REQUEST request) {
    return protocolVersion(request);
  }

  /**
   * This method is deprecated and will be removed in the subsequent release.
   *
   * @deprecated Use {@link #getProtocolVersion(Object)} instead.
   */
  @Deprecated
  @Nullable
  default String protocolVersion(REQUEST request) {
    throw new UnsupportedOperationException(
        "This method is deprecated and will be removed in the subsequent release.");
  }

  @Nullable
  default String getUrl(REQUEST request) {
    return url(request);
  }

  /**
   * This method is deprecated and will be removed in the subsequent release.
   *
   * @deprecated Use {@link #getUrl(Object)} instead.
   */
  @Deprecated
  @Nullable
  default String url(REQUEST request) {
    throw new UnsupportedOperationException(
        "This method is deprecated and will be removed in the subsequent release.");
  }

  @Nullable
  default String getConversationId(REQUEST request) {
    return conversationId(request);
  }

  /**
   * This method is deprecated and will be removed in the subsequent release.
   *
   * @deprecated Use {@link #getConversationId(Object)} instead.
   */
  @Deprecated
  @Nullable
  default String conversationId(REQUEST request) {
    throw new UnsupportedOperationException(
        "This method is deprecated and will be removed in the subsequent release.");
  }

  @Nullable
  default Long getMessagePayloadSize(REQUEST request) {
    return messagePayloadSize(request);
  }

  /**
   * This method is deprecated and will be removed in the subsequent release.
   *
   * @deprecated Use {@link #getMessagePayloadSize(Object)} instead.
   */
  @Deprecated
  @Nullable
  default Long messagePayloadSize(REQUEST request) {
    throw new UnsupportedOperationException(
        "This method is deprecated and will be removed in the subsequent release.");
  }

  @Nullable
  default Long getMessagePayloadCompressedSize(REQUEST request) {
    return messagePayloadCompressedSize(request);
  }

  /**
   * This method is deprecated and will be removed in the subsequent release.
   *
   * @deprecated Use {@link #getMessagePayloadCompressedSize(Object)} instead.
   */
  @Deprecated
  @Nullable
  default Long messagePayloadCompressedSize(REQUEST request) {
    throw new UnsupportedOperationException(
        "This method is deprecated and will be removed in the subsequent release.");
  }

  @Nullable
  default String getMessageId(REQUEST request, @Nullable RESPONSE response) {
    return messageId(request, response);
  }

  /**
   * This method is deprecated and will be removed in the subsequent release.
   *
   * @deprecated Use {@link #getMessageId(Object, Object)} instead.
   */
  @Deprecated
  @Nullable
  default String messageId(REQUEST request, @Nullable RESPONSE response) {
    throw new UnsupportedOperationException(
        "This method is deprecated and will be removed in the subsequent release.");
  }

  /**
   * Extracts all values of header named {@code name} from the request, or an empty list if there
   * were none.
   *
   * <p>Implementations of this method <b>must not</b> return a null value; an empty list should be
   * returned instead.
   */
  // TODO: when removing header(), make sure this method returns emptyList() by default
  default List<String> getMessageHeader(REQUEST request, String name) {
    return header(request, name);
  }

  /**
   * Extracts all values of header named {@code name} from the request, or an empty list if there
   * were none.
   *
   * <p>Implementations of this method <b>must not</b> return a null value; an empty list should be
   * returned instead.
   *
   * <p>This method is deprecated and will be removed in the subsequent release.
   *
   * @deprecated Use {@link #getMessageHeader(Object, String)} instead.
   */
  @Deprecated
  default List<String> header(REQUEST request, String name) {
    return Collections.emptyList();
  }
}
