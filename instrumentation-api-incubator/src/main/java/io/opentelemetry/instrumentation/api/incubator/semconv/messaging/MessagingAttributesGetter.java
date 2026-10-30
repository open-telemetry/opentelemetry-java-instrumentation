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
   * Returns the name of the destination subscription from which a message is consumed, or {@code
   * null} if there is none.
   */
  @Nullable
  default String getDestinationSubscriptionName(REQUEST request) {
    return null;
  }

  /**
   * Returns the {@code error.type} value for the operation.
   *
   * <p>The default implementation returns the exception class name, or {@code null} if there is no
   * exception.
   *
   * <p>The cardinality of the error type should be low. The instrumentations implementing this
   * method are recommended to document the custom values they support.
   */
  @Nullable
  default String getErrorType(
      REQUEST request, @Nullable RESPONSE response, @Nullable Throwable error) {
    return error == null ? null : error.getClass().getName();
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
