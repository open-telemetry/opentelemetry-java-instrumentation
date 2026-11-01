/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.common.internal;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import java.security.Principal;
import org.junit.jupiter.api.Test;

class ServletAdditionalAttributesExtractorTest {

  private final Object request = new Object();

  @SuppressWarnings("unchecked")
  private final ServletAccessor<Object, Object> accessor = mock(ServletAccessor.class);

  @Test
  void capturesUserNameWhenEnabled() {
    Principal principal = () -> "principal";
    when(accessor.getRequestUserPrincipal(request)).thenReturn(principal);
    AttributesBuilder attributes = Attributes.builder();

    new ServletAdditionalAttributesExtractor<>(accessor, false, true)
        .onEnd(attributes, Context.root(), new ServletRequestContext<>(request), null, null);

    assertThat(attributes.build().asMap()).containsOnly(entry(stringKey("user.name"), "principal"));
  }

  @Test
  void doesNotCaptureUserNameByDefault() {
    AttributesBuilder attributes = Attributes.builder();

    new ServletAdditionalAttributesExtractor<>(accessor, false, false)
        .onEnd(attributes, Context.root(), new ServletRequestContext<>(request), null, null);

    assertThat(attributes.build()).isEqualTo(Attributes.empty());
    verify(accessor, never()).getRequestUserPrincipal(request);
  }
}
