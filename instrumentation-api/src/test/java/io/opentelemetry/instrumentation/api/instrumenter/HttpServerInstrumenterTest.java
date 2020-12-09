/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class HttpServerInstrumenterTest {
  @Test
  public void extractForwardedFor() {
    assertEquals("1.1.1.1", HttpServerInstrumenter.extractForwardedFor("for=1.1.1.1"));
  }

  @Test
  public void extractForwardedForCaps() {
    assertEquals("1.1.1.1", HttpServerInstrumenter.extractForwardedFor("For=1.1.1.1"));
  }

  @Test
  public void extractForwardedForMalformed() {
    assertNull(HttpServerInstrumenter.extractForwardedFor("for=;for=1.1.1.1"));
  }

  @Test
  public void extractForwardedForEmpty() {
    assertNull(HttpServerInstrumenter.extractForwardedFor(""));
  }

  @Test
  public void extractForwardedForEmptyValue() {
    assertNull(HttpServerInstrumenter.extractForwardedFor("for="));
  }

  @Test
  public void extractForwardedForEmptyValueWithSemicolon() {
    assertNull(HttpServerInstrumenter.extractForwardedFor("for=;"));
  }

  @Test
  public void extractForwardedForNoFor() {
    assertNull(HttpServerInstrumenter.extractForwardedFor("by=1.1.1.1;test=1.1.1.1"));
  }

  @Test
  public void extractForwardedForMultiple() {
    assertEquals("1.1.1.1", HttpServerInstrumenter.extractForwardedFor("for=1.1.1.1;for=1.2.3.4"));
  }

  @Test
  public void extractForwardedForMixedSplitter() {
    assertEquals(
        "1.1.1.1",
        HttpServerInstrumenter.extractForwardedFor(
            "test=abcd; by=1.2.3.4, for=1.1.1.1;for=1.2.3.4"));
  }
}
