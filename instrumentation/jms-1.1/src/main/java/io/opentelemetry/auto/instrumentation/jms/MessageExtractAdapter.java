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
package io.opentelemetry.auto.instrumentation.jms;

import io.opentelemetry.context.propagation.HttpTextFormat;
import javax.jms.JMSException;
import javax.jms.Message;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MessageExtractAdapter implements HttpTextFormat.Getter<Message> {

  public static final MessageExtractAdapter GETTER = new MessageExtractAdapter();

  @Override
  public String get(final Message carrier, final String key) {
    final String propName = key.replace("-", MessageInjectAdapter.DASH);
    final Object value;
    try {
      value = carrier.getObjectProperty(propName);
    } catch (final JMSException e) {
      throw new RuntimeException(e);
    }
    if (value instanceof String) {
      return (String) value;
    } else {
      return null;
    }
  }
}
