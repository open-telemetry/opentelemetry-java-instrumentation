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
package io.opentelemetry.auto.instrumentation.khttp;

import io.opentelemetry.context.propagation.HttpTextFormat;
import java.util.HashMap;
import java.util.Map;

public class KHttpHeadersInjectAdapter implements HttpTextFormat.Setter<Map<String, String>> {

  private static Class emptyMap;

  static {
    try {
      emptyMap = Class.forName("kotlin.collections.EmptyMap");
    } catch (ClassNotFoundException e) {
    }
  }

  public static Map<String, String> asWritable(Map<String, String> headers) {
    // EmptyMap is read-only so we have to substitute it with writable instance to be able to inject
    // headers
    if (emptyMap != null && emptyMap.isInstance(headers)) {
      return new HashMap<>();
    } else {
      return headers;
    }
  }

  public static final KHttpHeadersInjectAdapter SETTER = new KHttpHeadersInjectAdapter();

  @Override
  public void set(Map<String, String> carrier, String key, String value) {
    carrier.put(key, value);
  }
}
