/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.openai.v1_1;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

import io.opentelemetry.api.common.AttributeKey;

// copied from GenAiIncubatingAttributes
final class GenAiAttributes {
  static final AttributeKey<String> GEN_AI_SYSTEM = stringKey("gen_ai.system");

  static final class GenAiOperationNameIncubatingValues {
    static final String CHAT = "chat";
    static final String EMBEDDING = "embeddings";

    private GenAiOperationNameIncubatingValues() {}
  }

  static final class GenAiSystemIncubatingValues {
    static final String OPENAI = "openai";

    private GenAiSystemIncubatingValues() {}
  }

  private GenAiAttributes() {}
}
