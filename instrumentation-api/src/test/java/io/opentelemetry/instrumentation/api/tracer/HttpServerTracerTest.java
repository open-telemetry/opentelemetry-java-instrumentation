/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class HttpServerTracerTest {
  @Test
  public void extractForwarded() {
    assertEquals("1.1.1.1", HttpServerTracer.extractForwarded("for=1.1.1.1"));
  }

  @Test
  public void extractForwardedIpv6() {
    assertEquals(
        "1111:1111:1111:1111:1111:1111:1111:1111",
        HttpServerTracer.extractForwarded("for=\"[1111:1111:1111:1111:1111:1111:1111:1111]\""));
  }

  @Test
  public void extractForwardedWithPort() {
    assertEquals("1.1.1.1", HttpServerTracer.extractForwarded("for=\"1.1.1.1:2222\""));
  }

  @Test
  public void extractForwardedIpv6WithPort() {
    assertEquals(
        "1111:1111:1111:1111:1111:1111:1111:1111",
        HttpServerTracer.extractForwarded(
            "for=\"[1111:1111:1111:1111:1111:1111:1111:1111]:2222\""));
  }

  @Test
  public void extractForwardedCaps() {
    assertEquals("1.1.1.1", HttpServerTracer.extractForwarded("For=1.1.1.1"));
  }

  @Test
  public void extractForwardedMalformed() {
    assertNull(HttpServerTracer.extractForwarded("for=;for=1.1.1.1"));
  }

  @Test
  public void extractForwardedEmpty() {
    assertNull(HttpServerTracer.extractForwarded(""));
  }

  @Test
  public void extractForwardedEmptyValue() {
    assertNull(HttpServerTracer.extractForwarded("for="));
  }

  @Test
  public void extractForwardedEmptyValueWithSemicolon() {
    assertNull(HttpServerTracer.extractForwarded("for=;"));
  }

  @Test
  public void extractForwardedNoFor() {
    assertNull(HttpServerTracer.extractForwarded("by=1.1.1.1;test=1.1.1.1"));
  }

  @Test
  public void extractForwardedMultiple() {
    assertEquals("1.1.1.1", HttpServerTracer.extractForwarded("for=1.1.1.1;for=1.2.3.4"));
  }

  @Test
  public void extractForwardedMultipleIpV6() {
    assertEquals(
        "1111:1111:1111:1111:1111:1111:1111:1111",
        HttpServerTracer.extractForwarded(
            "for=\"[1111:1111:1111:1111:1111:1111:1111:1111]\";for=1.2.3.4"));
  }

  @Test
  public void extractForwardedMultipleWithPort() {
    assertEquals("1.1.1.1", HttpServerTracer.extractForwarded("for=\"1.1.1.1:2222\";for=1.2.3.4"));
  }

  @Test
  public void extractForwardedMultipleIpV6WithPort() {
    assertEquals(
        "1111:1111:1111:1111:1111:1111:1111:1111",
        HttpServerTracer.extractForwarded(
            "for=\"[1111:1111:1111:1111:1111:1111:1111:1111]:2222\";for=1.2.3.4"));
  }

  @Test
  public void extractForwardedMixedSplitter() {
    assertEquals(
        "1.1.1.1",
        HttpServerTracer.extractForwarded("test=abcd; by=1.2.3.4, for=1.1.1.1;for=1.2.3.4"));
  }

  @Test
  public void extractForwardedMixedSplitterIpv6() {
    assertEquals(
        "1111:1111:1111:1111:1111:1111:1111:1111",
        HttpServerTracer.extractForwarded(
            "test=abcd; by=1.2.3.4, for=\"[1111:1111:1111:1111:1111:1111:1111:1111]\";for=1.2.3.4"));
  }

  @Test
  public void extractForwardedMixedSplitterWithPort() {
    assertEquals(
        "1.1.1.1",
        HttpServerTracer.extractForwarded(
            "test=abcd; by=1.2.3.4, for=\"1.1.1.1:2222\";for=1.2.3.4"));
  }

  @Test
  public void extractForwardedMixedSplitterIpv6WithPort() {
    assertEquals(
        "1111:1111:1111:1111:1111:1111:1111:1111",
        HttpServerTracer.extractForwarded(
            "test=abcd; by=1.2.3.4, for=\"[1111:1111:1111:1111:1111:1111:1111:1111]:2222\";for=1.2.3.4"));
  }

  @Test
  public void extractForwardedFor() {
    assertEquals("1.1.1.1", HttpServerTracer.extractForwardedFor("1.1.1.1"));
  }

  @Test
  public void extractForwardedForIpv6() {
    assertEquals(
        "1111:1111:1111:1111:1111:1111:1111:1111",
        HttpServerTracer.extractForwardedFor("\"[1111:1111:1111:1111:1111:1111:1111:1111]\""));
  }

  @Test
  public void extractForwardedForIpv6Unquoted() {
    assertEquals(
        "1111:1111:1111:1111:1111:1111:1111:1111",
        HttpServerTracer.extractForwardedFor("[1111:1111:1111:1111:1111:1111:1111:1111]"));
  }

  @Test
  public void extractForwardedForIpv6Unbracketed() {
    assertEquals(
        "1111:1111:1111:1111:1111:1111:1111:1111",
        HttpServerTracer.extractForwardedFor("1111:1111:1111:1111:1111:1111:1111:1111"));
  }

  @Test
  public void extractForwardedForWithPort() {
    assertEquals("1.1.1.1", HttpServerTracer.extractForwardedFor("1.1.1.1:2222"));
  }

  @Test
  public void extractForwardedForIpv6WithPort() {
    assertEquals(
        "1111:1111:1111:1111:1111:1111:1111:1111",
        HttpServerTracer.extractForwardedFor("\"[1111:1111:1111:1111:1111:1111:1111:1111]:2222\""));
  }

  @Test
  public void extractForwardedForIpv6UnquotedWithPort() {
    assertEquals(
        "1111:1111:1111:1111:1111:1111:1111:1111",
        HttpServerTracer.extractForwardedFor("[1111:1111:1111:1111:1111:1111:1111:1111]:2222"));
  }

  @Test
  public void extractForwardedForEmpty() {
    assertNull(HttpServerTracer.extractForwardedFor(""));
  }

  @Test
  public void extractForwardedForMultiple() {
    assertEquals("1.1.1.1", HttpServerTracer.extractForwardedFor("1.1.1.1,1.2.3.4"));
  }

  @Test
  public void extractForwardedForMultipleIpv6() {
    assertEquals(
        "1111:1111:1111:1111:1111:1111:1111:1111",
        HttpServerTracer.extractForwardedFor(
            "\"[1111:1111:1111:1111:1111:1111:1111:1111]\",1.2.3.4"));
  }

  @Test
  public void extractForwardedForMultipleIpv6Unquoted() {
    assertEquals(
        "1111:1111:1111:1111:1111:1111:1111:1111",
        HttpServerTracer.extractForwardedFor("[1111:1111:1111:1111:1111:1111:1111:1111],1.2.3.4"));
  }

  @Test
  public void extractForwardedForMultipleIpv6Unbracketed() {
    assertEquals(
        "1111:1111:1111:1111:1111:1111:1111:1111",
        HttpServerTracer.extractForwardedFor("1111:1111:1111:1111:1111:1111:1111:1111,1.2.3.4"));
  }

  @Test
  public void extractForwardedForMultipleWithPort() {
    assertEquals("1.1.1.1", HttpServerTracer.extractForwardedFor("1.1.1.1:2222,1.2.3.4"));
  }

  @Test
  public void extractForwardedForMultipleIpv6WithPort() {
    assertEquals(
        "1111:1111:1111:1111:1111:1111:1111:1111",
        HttpServerTracer.extractForwardedFor(
            "\"[1111:1111:1111:1111:1111:1111:1111:1111]:2222\",1.2.3.4"));
  }

  @Test
  public void extractForwardedForMultipleIpv6UnquotedWithPort() {
    assertEquals(
        "1111:1111:1111:1111:1111:1111:1111:1111",
        HttpServerTracer.extractForwardedFor(
            "[1111:1111:1111:1111:1111:1111:1111:1111]:2222,1.2.3.4"));
  }
}
