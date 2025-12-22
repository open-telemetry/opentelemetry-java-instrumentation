/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.openai.v1_1;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

import io.opentelemetry.api.common.AttributeKey;

// copied from GenAiIncubatingAttributes
final class GenAiAttributes {
  static final AttributeKey<String> GEN_AI_PROVIDER_NAME = stringKey("gen_ai.provider.name");

  static final class GenAiOperationNameIncubatingValues {
    static final String CHAT = "chat";
    static final String EMBEDDINGS = "embeddings";

    private GenAiOperationNameIncubatingValues() {}
  }

  static final class GenAiProviderNameIncubatingValues {
    static final String OPENAI = "openai";

    private GenAiProviderNameIncubatingValues() {}
  }

  static final class GenAiOutputTypeIncubatingValues {
    static final String TEXT = "text";

    static final String JSON = "json";

    private GenAiOutputTypeIncubatingValues() {}
  }

  private GenAiAttributes() {}
}
