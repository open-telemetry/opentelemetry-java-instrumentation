/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ibmmq;

import java.lang.ref.WeakReference;
import javax.annotation.Nullable;

// Dedicated VirtualField value type: a MessageListener->consumer association must not pin the
// consumer's session/connection/queue manager handle for the listener's whole lifetime, so the
// consumer is held weakly. A cleared/absent reference simply means "not available right now",
// letting a later delivery retry the read instead of being permanently short-circuited.
public class IbmMqConsumerHolder {

  private final WeakReference<Object> consumer;

  public IbmMqConsumerHolder(Object consumer) {
    this.consumer = new WeakReference<>(consumer);
  }

  @Nullable
  public Object consumer() {
    return consumer.get();
  }
}
