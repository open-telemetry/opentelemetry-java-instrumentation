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

package io.opentelemetry.instrumentation.armeria.v1_0.common;

import com.linecorp.armeria.common.RequestContext;
import io.grpc.Context;
import io.grpc.Context.Storage;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link Storage} override of the gRPC context that uses an Armeria {@link RequestContext} as
 * backing storage when available. Armeria provides idioms for propagating {@link RequestContext}
 * which are inherently tied to request processing, so by using it for our storage as well, we can
 * make sure a {@link io.opentelemetry.trace.Span} is propagated throughout the lifecycle of the
 * request with it.
 *
 * <p>Contexts created when an Armeria context is not available follow the same pattern as the gRPC
 * default of using a normal {@link ThreadLocal}.
 *
 * <p>This class is automatically picked up by gRPC by {@link
 * io.grpc.override.ContextStorageOverride} provided in this artifact. If you use your own
 * implementation to customize storage, you should generally delegate to this class to ensure
 * context propagates with the Armeria context.
 */
public class ArmeriaContextStorageOverride extends Storage {

  private static final Logger logger = LoggerFactory.getLogger(ArmeriaContextStorageOverride.class);

  private static final ThreadLocal<Context> LOCAL_CONTEXT = new ThreadLocal<>();

  private static final AttributeKey<Context> CONTEXT =
      AttributeKey.valueOf(ArmeriaContextStorageOverride.class, "CONTEXT");

  @Override
  public Context doAttach(Context toAttach) {
    RequestContext armeriaCtx = RequestContext.currentOrNull();
    Context current = current(armeriaCtx);
    if (armeriaCtx != null) {
      armeriaCtx.setAttr(CONTEXT, toAttach);
    } else {
      LOCAL_CONTEXT.set(toAttach);
    }
    return current;
  }

  @Override
  public void detach(Context toDetach, Context toRestore) {
    RequestContext armeriaCtx = RequestContext.currentOrNull();
    Context current = current(armeriaCtx);
    if (current != toDetach) {
      // Log a warning instead of throwing an exception as the context to attach is assumed
      // to be the correct one and the unbalanced state represents a coding mistake in a lower
      // layer in the stack that cannot be recovered from here.
      if (logger.isWarnEnabled()) {
        logger.warn("Context was not attached when detaching", new Throwable().fillInStackTrace());
      }
    }

    if (toRestore == Context.ROOT) {
      toRestore = null;
    }
    if (armeriaCtx != null) {
      // We do not ever restore the ROOT context when in the context of an Armeria request. The
      // context's lifecycle is effectively bound to the request, even through asynchronous flows,
      // so we do not ever need to clear it explicitly. It will disappear along with the request
      // when it's done.
      if (toRestore != null) {
        armeriaCtx.setAttr(CONTEXT, toRestore);
      }
    } else {
      LOCAL_CONTEXT.set(toRestore);
    }
  }

  @Override
  public Context current() {
    RequestContext armeriaCtx = RequestContext.currentOrNull();
    return current(armeriaCtx);
  }

  private static Context current(RequestContext armeriaCtx) {
    final Context current;
    if (armeriaCtx != null) {
      current = armeriaCtx.attr(CONTEXT);
    } else {
      current = LOCAL_CONTEXT.get();
    }
    return current != null ? current : Context.ROOT;
  }
}
