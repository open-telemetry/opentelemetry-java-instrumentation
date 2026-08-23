/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jsf.mojarra.v1_2;

import io.opentelemetry.javaagent.instrumentation.jsf.common.javax.BaseJsfTest;

class Mojarra2Test extends BaseJsfTest {
  @Override
  protected String getJsfVersion() {
    return "2";
  }
}
