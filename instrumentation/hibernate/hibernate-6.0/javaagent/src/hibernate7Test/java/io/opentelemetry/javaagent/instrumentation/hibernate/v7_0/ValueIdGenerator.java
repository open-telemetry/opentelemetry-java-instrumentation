/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate.v7_0;

import java.util.concurrent.atomic.AtomicLong;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.enhanced.SequenceStyleGenerator;

public class ValueIdGenerator extends SequenceStyleGenerator {
  private final AtomicLong counter = new AtomicLong();

  @Override
  public Object generate(SharedSessionContractImplementor session, Object owner) {
    return counter.incrementAndGet();
  }
}
