package io.opentelemetry.auto.bootstrap.instrumentation.decorator;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class HttpServerTracerTest {
  @Test
  public void extractForwardedFor() {
    assertEquals("1.1.1.1", HttpServerTracer.extractForwardedFor("for=1.1.1.1"));
  }

  @Test
  public void extractForwardedForMalformed() {
    assertNull(HttpServerTracer.extractForwardedFor("for=;for=1.1.1.1"));
  }

  @Test
  public void extractForwardedForEmpty() {
    assertNull(HttpServerTracer.extractForwardedFor(""));
  }

  @Test
  public void extractForwardedForEmptyValue() {
    assertNull(HttpServerTracer.extractForwardedFor("for="));
  }

  @Test
  public void extractForwardedForEmptyValueWithSemicolon() {
    assertNull(HttpServerTracer.extractForwardedFor("for=;"));
  }

  @Test
  public void extractForwardedForNoFor() {
    assertNull(HttpServerTracer.extractForwardedFor("by=1.1.1.1;test=1.1.1.1"));
  }

  @Test
  public void extractForwardedForMultiple() {
    assertEquals("1.1.1.1", HttpServerTracer.extractForwardedFor("for=1.1.1.1;for=1.2.3.4"));
  }

  @Test
  public void extractForwardedForMixedSplitter() {
    assertEquals("1.1.1.1",
        HttpServerTracer.extractForwardedFor("test=abcd; by=1.2.3.4, for=1.1.1.1;for=1.2.3.4"));
  }
}