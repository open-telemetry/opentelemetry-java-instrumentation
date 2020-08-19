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

package io.grpc.override;

import com.linecorp.armeria.common.RequestContext;
import io.grpc.Context;
import io.grpc.Context.Storage;
import io.opentelemetry.instrumentation.armeria.v1_0.common.ArmeriaContextStorageOverride;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link Storage} override of the gRPC context that uses an Armeria {@link RequestContext} as
 * backing storage when available. Armeria provides idioms for propagating {@link RequestContext}
 * which are inherently tied to request processing, so by using it for our storage as well, we can
 * make sure a {@link io.opentelemetry.trace.Span} is propagated throughout the lifecycle of the
 * request with it.
 *
 * <p>This class is automatically picked up by gRPC because it follows a specific package / class
 * naming convention.
 *
 * <p>Contexts created when an Armeria context is not available follow the same pattern as the gRPC
 * default of using a normal {@link ThreadLocal}.
 */
public class ContextStorageOverride extends Storage {

  private static final Logger logger = LoggerFactory.getLogger(ContextStorageOverride.class);

  private static final ArmeriaContextStorageOverride DELEGATE = new ArmeriaContextStorageOverride();

  @Override
  public Context doAttach(Context toAttach) {
    return DELEGATE.doAttach(toAttach);
  }

  @Override
  public void detach(Context toDetach, Context toRestore) {
    DELEGATE.detach(toDetach, toRestore);
  }

  @Override
  public Context current() {
    return DELEGATE.current();
  }
}
