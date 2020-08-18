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

package io.opentelemetry.instrumentation.auto.jaxrsclient.v2_0;

import io.opentelemetry.context.propagation.HttpTextFormat;
import javax.ws.rs.client.ClientRequestContext;

public final class InjectAdapter implements HttpTextFormat.Setter<ClientRequestContext> {

  public static final InjectAdapter SETTER = new InjectAdapter();

  @Override
  public void set(ClientRequestContext carrier, String key, String value) {
    // Don't allow duplicates.
    carrier.getHeaders().putSingle(key, value);
  }
}
