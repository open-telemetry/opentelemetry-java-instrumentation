/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.messaging;

import static java.util.Collections.emptyList;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
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
  String getSystem(REQUEST request);

  @Nullable
  String getDestinationKind(REQUEST request);

  @Nullable
  String getDestination(REQUEST request);

  boolean isTemporaryDestination(REQUEST request);

  /**
   * Returns the application protocol used.
   *
   * @deprecated Use {@link NetClientAttributesGetter#getProtocolName(Object, Object)} instead.
   */
  @Deprecated
  @Nullable
  default String getProtocol(REQUEST request) {
    return null;
  }

  /**
   * Returns the version of the application protocol used.
   *
   * @deprecated Use {@link NetClientAttributesGetter#getProtocolVersion(Object, Object)} instead.
   */
  @Deprecated
  @Nullable
  default String getProtocolVersion(REQUEST request) {
    return null;
  }

  /**
   * Returns the application protocol used.
   *
   * @deprecated The {@code messaging.url} attribute was removed without replacement.
   */
  @Deprecated
  @Nullable
  default String getUrl(REQUEST request) {
    return null;
  }

  @Nullable
  String getConversationId(REQUEST request);

  @Nullable
  Long getMessagePayloadSize(REQUEST request);

  @Nullable
  Long getMessagePayloadCompressedSize(REQUEST request);

  @Nullable
  String getMessageId(REQUEST request, @Nullable RESPONSE response);

  /**
   * Extracts all values of header named {@code name} from the request, or an empty list if there
   * were none.
   *
   * <p>Implementations of this method <b>must not</b> return a null value; an empty list should be
   * returned instead.
   */
  default List<String> getMessageHeader(REQUEST request, String name) {
    return emptyList();
  }
}
