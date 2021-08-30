/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.chunk;

import org.springframework.batch.core.scope.context.ChunkContext;

class ChunkContextAndBuilder {
  final ChunkContext chunkContext;
  final Class<?> builderClass;

  ChunkContextAndBuilder(ChunkContext chunkContext, Class<?> builderClass) {
    this.chunkContext = chunkContext;
    this.builderClass = builderClass;
  }
}
