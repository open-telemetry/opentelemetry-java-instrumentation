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
   * Returns the name of the destination subscription from which a message is consumed, or {@code
   * null} if there is none.
   *
   * <p>This attribute only exists in the v1.43 messaging semantic conventions.
   */
  @Nullable
  default String getDestinationSubscriptionName(REQUEST request) {
    return null;
  }

  /**
   * Returns a description of a class of error the operation ended with.
   *
   * <p>If this method returns {@code null}, the exception class name (if any) will be used as error
   * type.
   *
   * <p>The cardinality of the error type should be low. The instrumentations implementing this
   * method are recommended to document the custom values they support.
   */
  @Nullable
  default String getErrorType(
      REQUEST request, @Nullable RESPONSE response, @Nullable Throwable error) {
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

  /**
   * Extracts the names of all headers present on the request, or an empty iterable if there were
   * none.
   *
   * <p>This is used to resolve header selectors that cannot be turned into a list of exact header
   * names, such as selectors containing wildcard patterns or selectors that only exclude headers.
   * Selectors that only list exact header names are resolved with {@link #getMessageHeader(Object,
   * String)} alone. To preserve compatibility with existing implementations, overriding this method
   * is optional until 3.0.
   *
   * <p>Implementations of this method <b>must not</b> return a null value; an empty iterable should
   * be returned instead. The returned iterable is only read, so implementations may return a view
   * over the underlying header names as long as that view cannot change while the returned iterable
   * is being read.
   */
  // TODO: remove the default implementation and make this required to implement in 3.0
  default Iterable<String> getMessageHeaderNames(REQUEST request) {
    return emptyList();
  }
}
