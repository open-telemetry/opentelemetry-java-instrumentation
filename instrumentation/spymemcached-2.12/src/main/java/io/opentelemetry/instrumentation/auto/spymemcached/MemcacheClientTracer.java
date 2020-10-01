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

package io.opentelemetry.instrumentation.auto.spymemcached;

import io.opentelemetry.instrumentation.api.tracer.DatabaseClientTracer;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.attributes.SemanticAttributes;
import java.net.InetSocketAddress;
import net.spy.memcached.MemcachedConnection;

public class MemcacheClientTracer extends DatabaseClientTracer<MemcachedConnection, String> {
  public static final MemcacheClientTracer TRACER = new MemcacheClientTracer();

  @Override
  protected String dbSystem(MemcachedConnection memcachedConnection) {
    return "memcached";
  }

  @Override
  protected InetSocketAddress peerAddress(MemcachedConnection memcachedConnection) {
    return null;
  }

  @Override
  protected void onStatement(Span span, String statement) {
    span.setAttribute(SemanticAttributes.DB_OPERATION, statement);
  }

  @Override
  protected String normalizeQuery(String query) {
    char[] chars =
        query
            .replaceFirst("^async", "")
            // 'CAS' name is special, we have to lowercase whole name
            .replaceFirst("^CAS", "cas")
            .toCharArray();

    // Lowercase first letter
    chars[0] = Character.toLowerCase(chars[0]);

    return new String(chars);
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.spymemcached-2.12";
  }
}
