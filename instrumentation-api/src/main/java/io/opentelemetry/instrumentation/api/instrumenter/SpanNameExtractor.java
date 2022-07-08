/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

/**
 * Extractor of the span name for a request. Where possible, an extractor based on semantic
 * conventions returned from the factories in this class should be used. The most common reason to
 * provide a custom implementation would be to apply the extractor for a particular semantic
 * convention by first determining which convention applies to the request.
 * 请求的span名称提取器。在可能的情况下，应该使用基于从类中的工厂返回的语义约定的提取器。
 * 提供自定义实现的最常见原因是，通过首先确定哪个约定适用于请求，从而对特定的语义约定应用提取器。
 */
@FunctionalInterface
public interface SpanNameExtractor<REQUEST> {

  /** Returns the span name. */
  String extract(REQUEST request);
}
