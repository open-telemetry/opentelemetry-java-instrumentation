/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.jsr;

import javax.batch.api.chunk.ItemProcessor;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;

class TestItemProcessor implements ItemProcessor {
  @Override
  public Object processItem(Object item) {
    return Integer.parseInt(DefaultGroovyMethods.asType(item, String.class));
  }
}
