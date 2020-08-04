/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.instrumentation.library.api.decorator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class HttpServerTracerTest {
  @Test
  public void extractForwardedFor() {
    assertEquals("1.1.1.1", HttpServerTracer.extractForwardedFor("for=1.1.1.1"));
  }

  @Test
  public void extractForwardedForCaps() {
    assertEquals("1.1.1.1", HttpServerTracer.extractForwardedFor("For=1.1.1.1"));
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
    assertEquals(
        "1.1.1.1",
        HttpServerTracer.extractForwardedFor("test=abcd; by=1.2.3.4, for=1.1.1.1;for=1.2.3.4"));
  }
}
