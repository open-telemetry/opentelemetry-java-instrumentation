/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jsf.myfaces.v1_2;

import io.opentelemetry.javaagent.instrumentation.jsf.common.javax.BaseJsfTest;

class Myfaces2Test extends BaseJsfTest {
  @Override
  public String getJsfVersion() {
    return "2";
  }
}
