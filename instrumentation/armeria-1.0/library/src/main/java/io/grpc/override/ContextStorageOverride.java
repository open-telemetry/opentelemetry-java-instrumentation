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

import io.grpc.Context;
import io.grpc.Context.Storage;
import io.opentelemetry.instrumentation.armeria.v1_0.common.ArmeriaContextStorageOverride;

/**
 * Initialization point for overriding {@link Storage} with {@link ArmeriaContextStorageOverride}.
 */
public class ContextStorageOverride extends Storage {

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
