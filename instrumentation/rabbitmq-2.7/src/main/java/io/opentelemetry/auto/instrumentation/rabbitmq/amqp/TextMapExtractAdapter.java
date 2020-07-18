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

package io.opentelemetry.auto.instrumentation.rabbitmq.amqp;

import io.opentelemetry.context.propagation.HttpTextFormat;
import java.util.Map;

public class TextMapExtractAdapter implements HttpTextFormat.Getter<Map<String, Object>> {

  public static final TextMapExtractAdapter GETTER = new TextMapExtractAdapter();

  @Override
  public String get(final Map<String, Object> carrier, final String key) {
    Object obj = carrier.get(key);
    return obj == null ? null : obj.toString();
  }
}
