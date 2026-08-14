/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.openai.v1_1;

// copied from GenAiIncubatingAttributes
final class GenAiAttributes {

  static final class GenAiOperationNameIncubatingValues {
    static final String CHAT = "chat";
    static final String EMBEDDINGS = "embeddings";

    private GenAiOperationNameIncubatingValues() {}
  }

  static final class GenAiProviderNameIncubatingValues {
    static final String OPENAI = "openai";

    private GenAiProviderNameIncubatingValues() {}
  }

  private GenAiAttributes() {}
}
