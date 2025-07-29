/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.myfaces;

import io.opentelemetry.javaagent.instrumentation.jsf.javax.BaseJsfTest;

class Myfaces12Test extends BaseJsfTest {
  @Override
  public String getJsfVersion() {
    return "1.2";
  }
}
