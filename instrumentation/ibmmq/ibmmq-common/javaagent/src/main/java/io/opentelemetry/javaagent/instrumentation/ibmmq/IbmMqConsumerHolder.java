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
// letting a later delivery retry the read instead of being permanently short-circuited. An
// ambiguous holder is different again: it means this listener has been observed serving consumers
// bound to more than one queue manager, so no QMID may ever be attributed to it, not even on
// retry -- a cleared reference still permits a retry, an ambiguous one never does.
public class IbmMqConsumerHolder {

  private final WeakReference<Object> consumer;
  private final boolean ambiguous;

  public IbmMqConsumerHolder(Object consumer) {
    this.consumer = new WeakReference<>(consumer);
    this.ambiguous = false;
  }

  private IbmMqConsumerHolder(boolean ambiguous) {
    this.consumer = new WeakReference<>(null);
    this.ambiguous = ambiguous;
  }

  public static IbmMqConsumerHolder ambiguous() {
    return new IbmMqConsumerHolder(true);
  }

  @Nullable
  public Object consumer() {
    return consumer.get();
  }

  public boolean isAmbiguous() {
    return ambiguous;
  }
}
