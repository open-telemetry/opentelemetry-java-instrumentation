/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import java.util.List;

public interface ConditionalAttributesExtractorProvider {
  List<String> supportedNames();

  <REQUEST, RESPONSE> AttributesExtractor<REQUEST, RESPONSE> get();
}
