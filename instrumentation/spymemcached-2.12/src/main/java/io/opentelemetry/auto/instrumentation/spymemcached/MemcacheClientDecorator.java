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

package io.opentelemetry.auto.instrumentation.spymemcached;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.instrumentation.library.api.decorator.DatabaseClientDecorator;
import io.opentelemetry.trace.Tracer;
import net.spy.memcached.MemcachedConnection;

public class MemcacheClientDecorator extends DatabaseClientDecorator<MemcachedConnection> {
  public static final MemcacheClientDecorator DECORATE = new MemcacheClientDecorator();

  public static final Tracer TRACER =
      OpenTelemetry.getTracerProvider().get("io.opentelemetry.auto.spymemcached-2.12");

  @Override
  protected String dbSystem() {
    return "memcached";
  }

  @Override
  protected String dbUser(final MemcachedConnection session) {
    return null;
  }

  @Override
  protected String dbName(final MemcachedConnection connection) {
    return null;
  }

  public String spanNameOnOperation(final String methodName) {

    char[] chars =
        methodName
            .replaceFirst("^async", "")
            // 'CAS' name is special, we have to lowercase whole name
            .replaceFirst("^CAS", "cas")
            .toCharArray();

    // Lowercase first letter
    chars[0] = Character.toLowerCase(chars[0]);

    return new String(chars);
  }
}
