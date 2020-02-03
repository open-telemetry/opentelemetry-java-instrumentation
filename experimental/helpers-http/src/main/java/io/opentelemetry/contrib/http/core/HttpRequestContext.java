/*
 * Copyright 2020, OpenTelemetry Authors
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

package io.opentelemetry.contrib.http.core;

import static com.google.common.base.Preconditions.checkNotNull;

import io.opentelemetry.correlationcontext.CorrelationContext;
import io.opentelemetry.trace.Span;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nullable;

/** This class provides storage per request context on http client and server. */
public class HttpRequestContext {

  static final long INVALID_STARTTIME = -1;

  private final long requestStartTime;
  private final Span span;
  private final AtomicLong sentMessageSize = new AtomicLong();
  private final AtomicLong receiveMessageSize = new AtomicLong();
  private final AtomicLong sentSeqId = new AtomicLong();
  private final AtomicLong receviedSeqId = new AtomicLong();
  @Nullable private final CorrelationContext corrlatContext;

  HttpRequestContext(Span span, @Nullable CorrelationContext corrlatContext) {
    checkNotNull(span, "span is required");
    this.span = span;
    this.corrlatContext = corrlatContext;
    requestStartTime = System.nanoTime();
  }

  long getRequestStartTime() {
    return requestStartTime;
  }

  Span getSpan() {
    return span;
  }

  long getSentMessageSize() {
    return sentMessageSize.get();
  }

  void addSentMessageSize(long size) {
    sentMessageSize.addAndGet(size);
  }

  long getReceiveMessageSize() {
    return receiveMessageSize.get();
  }

  void addReceiveMessageSize(long size) {
    receiveMessageSize.addAndGet(size);
  }

  long getSentSeqId() {
    return sentSeqId.get();
  }

  long nextSentSeqId() {
    return sentSeqId.incrementAndGet();
  }

  long getReceviedSeqId() {
    return receviedSeqId.get();
  }

  long nextReceivedSeqId() {
    return receviedSeqId.incrementAndGet();
  }

  @Nullable
  CorrelationContext getCorrlatContext() {
    return corrlatContext;
  }
}
