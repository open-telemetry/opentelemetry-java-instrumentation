/*
 * Copyright 2020, OpenTelemetry Authors
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
package io.opentelemetry.auto.typed.http.delegate;

import io.opentelemetry.OpenTelemetry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link HttpTypedSpanDelegatingSpanTest}. */
@RunWith(JUnit4.class)
public class HttpTypedSpanDelegatingSpanTest {

  @Test
  public void exampleUsage_span() {
    // https://github.com/open-telemetry/opentelemetry-java/issues/778
    HttpServerSpan span =
        HttpServerSpan.createHttpServerSpan(
            OpenTelemetry.getTracerProvider().get("test-tracer"), "https://example.com")
            .setMethod("GET")
            .setServerName("example.com")
            .setRoute("/webshop/articles/:article_id")
            .setClientIp("192.0.2.4")
            .setFlavor("1.1")
            // this will be replaced by the set in the Span
            .setNetHostName("unknown.com")
            .start();
    span.setStatusCode(200)
        .setStatusText("OK")
        .setScheme("https")
        .setNetHostName("example.com")
        .setNetHostPort(80)
        .setTarget("/webshop/articles/socks");
    span.end();
  }

  @Test
  public void testAttributeEnumBit_builder() {
    HttpSpan.HttpSpanBuilder span =
        HttpSpan.createHttpSpan(
            OpenTelemetry.getTracerProvider().get("test-tracer"), "https://example.com")
            .setMethod("GET")
            .setFlavor("1.1");
    HttpServerSpan serverSpan = HttpServerSpan.createHttpServerSpan(span).start();
    serverSpan
        .setStatusCode(200)
        .setStatusText("OK")
        .setScheme("https")
        .setUrl("https://foo.invalid")
        .setNetHostName("example.com")
        .setNetHostPort(80)
        .setTarget("/webshop/articles/socks");
    serverSpan.end();
  }
}
