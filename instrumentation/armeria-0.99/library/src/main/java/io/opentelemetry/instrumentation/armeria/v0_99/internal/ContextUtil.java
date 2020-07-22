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

package io.opentelemetry.instrumentation.armeria.v0_99.internal;

import com.linecorp.armeria.common.RequestContext;
import io.grpc.Context;
import io.netty.util.AttributeKey;

public final class ContextUtil {

  private static final AttributeKey<Context> CONTEXT_KEY =
      AttributeKey.valueOf(Context.class, "CONTEXT");

  public static void attachContext(Context context, RequestContext armeriaCtx) {
    armeriaCtx.setAttr(CONTEXT_KEY, context);
  }

  public static Context getContext(RequestContext armeriaCtx) {
    return armeriaCtx.attr(CONTEXT_KEY);
  }

  private ContextUtil() {}
}
