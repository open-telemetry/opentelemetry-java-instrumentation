/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.dropwizardviews;

import io.dropwizard.views.View;
import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeAttributesGetter;
import javax.annotation.Nullable;

public class ViewCodeAttributesGetter implements CodeAttributesGetter<View> {

  @Nullable
  @Override
  public Class<?> getCodeClass(View view) {
    return view.getClass();
  }

  @Nullable
  @Override
  public String getMethodName(View view) {
    return "render";
  }
}
