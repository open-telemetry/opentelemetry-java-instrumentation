/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.messaging;

import static java.util.Collections.emptyList;

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
  String getDestination(REQUEST request);

  @Nullable
  String getDestinationTemplate(REQUEST request);

  boolean isTemporaryDestination(REQUEST request);

  boolean isAnonymousDestination(REQUEST request);

  @Nullable
  String getConversationId(REQUEST request);

  @Nullable
  @Deprecated
  default Long getMessagePayloadSize(REQUEST request) {
    return null;
  }

  @Nullable
  @Deprecated
  default Long getMessagePayloadCompressedSize(REQUEST request) {
    return null;
  }

  @Nullable
  Long getMessageBodySize(REQUEST request);

  @Nullable
  Long getMessageEnvelopeSize(REQUEST request);

  @Nullable
  String getMessageId(REQUEST request, @Nullable RESPONSE response);

  @Nullable
  String getClientId(REQUEST request);

  @Nullable
  Long getBatchMessageCount(REQUEST request, @Nullable RESPONSE response);

  @Nullable
  default String getDestinationPartitionId(REQUEST request) {
    return null;
  }

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
