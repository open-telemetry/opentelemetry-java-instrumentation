/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.restlet.v2_0.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.restlet.Response;
import org.restlet.util.Series;

class MessageAttributesAccessorTest {

  @Test
  void getsMessageAttributes() {
    Response response = new Response(null);

    assertThat(MessageAttributesAccessor.getAttributes(response))
        .isSameAs(response.getAttributes());
  }

  @Test
  void setsAndReplacesHeaderValue() {
    Series<?> headers = MessageAttributesAccessor.createHeaderSeries();
    assertThat(headers).isNotNull();

    MessageAttributesAccessor.setSeriesValue(headers, "X-Test", "first");
    assertThat(headers.getFirstValue("X-Test")).isEqualTo("first");

    MessageAttributesAccessor.setSeriesValue(headers, "X-Test", "second");
    assertThat(headers.getValuesArray("X-Test")).containsExactly("second");
  }
}
