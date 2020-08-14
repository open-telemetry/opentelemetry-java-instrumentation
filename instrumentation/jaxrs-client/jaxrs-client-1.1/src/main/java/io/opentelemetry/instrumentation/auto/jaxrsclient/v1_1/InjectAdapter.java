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

package io.opentelemetry.instrumentation.auto.jaxrsclient.v1_1;

import com.sun.jersey.api.client.ClientRequest;
import io.opentelemetry.context.propagation.HttpTextFormat;

public final class InjectAdapter implements HttpTextFormat.Setter<ClientRequest> {

  public static final InjectAdapter SETTER = new InjectAdapter();

  @Override
  public void set(ClientRequest clientRequest, String key, String value) {
    clientRequest.getHeaders().putSingle(key, value);
  }
}
