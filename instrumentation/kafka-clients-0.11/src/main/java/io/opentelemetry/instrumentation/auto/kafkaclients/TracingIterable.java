/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.instrumentation.auto.kafkaclients;

import java.util.Iterator;
import org.apache.kafka.clients.consumer.ConsumerRecord;

public class TracingIterable implements Iterable<ConsumerRecord> {
  private final Iterable<ConsumerRecord> delegate;
  private final KafkaDecorator decorator;
  private boolean firstIterator = true;

  public TracingIterable(final Iterable<ConsumerRecord> delegate, final KafkaDecorator decorator) {
    this.delegate = delegate;
    this.decorator = decorator;
  }

  @Override
  public Iterator<ConsumerRecord> iterator() {
    Iterator<ConsumerRecord> it;
    // We should only return one iterator with tracing.
    // However, this is not thread-safe, but usually the first (hopefully only) traversal of
    // ConsumerRecords is performed in the same thread that called poll()
    if (firstIterator) {
      it = new TracingIterator(delegate.iterator(), decorator);
      firstIterator = false;
    } else {
      it = delegate.iterator();
    }

    return it;
  }
}
