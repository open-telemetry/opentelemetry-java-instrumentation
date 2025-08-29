/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nats.v2_17.internal;

import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesGetter;

class NatsRequestMessagingAttributesGetter {

  static final MessagingAttributesGetter<NatsRequest, Object> INSTANCE =
      NatsRequestMessagingAttributesGetterFactory.create();

  private NatsRequestMessagingAttributesGetter() {}
}
