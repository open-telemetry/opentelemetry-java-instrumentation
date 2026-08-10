/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rocketmqclient.v5_0;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import org.apache.rocketmq.client.apis.message.MessageView;

public class SimpleConsumerTracingList extends ArrayList<MessageView> {
  private static final long serialVersionUID = 1L;

  private boolean firstIterator = true;

  public static List<MessageView> wrap(List<MessageView> messages) {
    return new SimpleConsumerTracingList(messages);
  }

  private SimpleConsumerTracingList(List<MessageView> messages) {
    super(messages);
  }

  @Override
  public Iterator<MessageView> iterator() {
    if (firstIterator) {
      firstIterator = false;
      return SimpleConsumerTracingIterator.wrap(super.iterator());
    }
    return super.iterator();
  }

  @Override
  public void forEach(Consumer<? super MessageView> action) {
    for (MessageView message : this) {
      action.accept(message);
    }
  }
}
