/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.log.internal;

import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MdcContextTest {
  @Test
  void retainsOriginalValuesAcrossEnrichment() {
    Map<String, String> applicationMdc = singletonMap("key", "application");
    MdcContext enriched = new MdcContext(applicationMdc);
    enriched.put("key", "enriched");
    enriched.put("other", "value");
    MdcContext nested = new MdcContext(enriched);
    nested.put("key", "nested");

    assertThat(nested).containsOnly(entry("key", "nested"), entry("other", "value"));
    assertThat(MdcContext.getApplicationMdc(nested)).isSameAs(applicationMdc);
    assertThat(MdcContext.getApplicationMdc(enriched)).containsOnly(entry("key", "application"));
    assertThat(MdcContext.getApplicationMdc(applicationMdc)).isSameAs(applicationMdc);
  }

  @Test
  void handlesAbsentApplicationMdc() {
    MdcContext enriched = new MdcContext(null);
    enriched.put("key", "enriched");

    assertThat(enriched).containsOnly(entry("key", "enriched"));
    assertThat(MdcContext.getApplicationMdc(enriched)).isEmpty();
  }

  @Test
  void serializesAsPlainMap() throws Exception {
    MdcContext enriched = new MdcContext(singletonMap("key", "application"));
    enriched.put("key", "enriched");
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(enriched);
    }

    try (ObjectInputStream input =
        new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      assertThat(input.readObject())
          .isExactlyInstanceOf(HashMap.class)
          .isEqualTo(singletonMap("key", "enriched"));
    }
  }
}
