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

package io.opentelemetry.auto.instrumentation.jaxrsclient.v2_0;

import javax.ws.rs.core.Feature;
import javax.ws.rs.core.FeatureContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientTracingFeature implements Feature {

  private static final Logger log = LoggerFactory.getLogger(ClientTracingFeature.class);

  @Override
  public boolean configure(final FeatureContext context) {
    context.register(new ClientTracingFilter());
    log.debug("ClientTracingFilter registered");
    return true;
  }
}
