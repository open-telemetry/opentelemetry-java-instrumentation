/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.messaging;

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
}
