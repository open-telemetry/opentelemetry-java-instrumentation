/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.openai.v1_1;

// copied from GenAiIncubatingAttributes
final class GenAiAttributes {
  static final class GenAiOperationNameIncubatingValues {
    static final String CHAT = "chat";

    private GenAiOperationNameIncubatingValues() {}
  }

  static final class GenAiSystemIncubatingValues {
    static final String OPENAI = "openai";

    private GenAiSystemIncubatingValues() {}
  }

  private GenAiAttributes() {}
}
