/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nats.v2_17.internal;

import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesGetter;

class NatsRequestMessagingAttributesGetter {

  static final MessagingAttributesGetter<NatsRequest, Void> VOID_INSTANCE =
      NatsRequestMessagingAttributesGetterFactory.create();

  static final MessagingAttributesGetter<NatsRequest, NatsRequest> NATS_REQUEST_INSTANCE =
      NatsRequestMessagingAttributesGetterFactory.create();

  private NatsRequestMessagingAttributesGetter() {}
}
