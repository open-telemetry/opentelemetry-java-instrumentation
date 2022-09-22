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
  String system(REQUEST request);

  @Nullable
  String destinationKind(REQUEST request);

  @Nullable
  String destination(REQUEST request);

  boolean temporaryDestination(REQUEST request);

  @Nullable
  String protocol(REQUEST request);

  @Nullable
  String protocolVersion(REQUEST request);

  @Nullable
  String url(REQUEST request);

  @Nullable
  String conversationId(REQUEST request);

  @Nullable
  Long messagePayloadSize(REQUEST request);

  @Nullable
  Long messagePayloadCompressedSize(REQUEST request);

  @Nullable
  String messageId(REQUEST request, @Nullable RESPONSE response);

  /**
   * Extracts all values of header named {@code name} from the request, or an empty list if there
   * were none.
   *
   * <p>Implementations of this method <b>must not</b> return a null value; an empty list should be
   * returned instead.
   */
  default List<String> header(REQUEST request, String name) {
    return Collections.emptyList();
  }
}
