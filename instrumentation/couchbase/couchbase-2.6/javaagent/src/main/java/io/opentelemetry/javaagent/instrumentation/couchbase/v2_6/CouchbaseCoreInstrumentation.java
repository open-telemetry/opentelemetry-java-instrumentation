/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_6;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.couchbase.client.core.message.CouchbaseRequest;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.field.VirtualField;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.couchbase.v2_0.CouchbaseRequestInfo;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class CouchbaseCoreInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.couchbase.client.core.CouchbaseCore");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isPublic())
            .and(takesArgument(0, named("com.couchbase.client.core.message.CouchbaseRequest")))
            .and(named("send")),
        CouchbaseCoreInstrumentation.class.getName() + "$CouchbaseCoreAdvice");
  }

  @SuppressWarnings("unused")
  public static class CouchbaseCoreAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void addOperationIdToSpan(@Advice.Argument(0) CouchbaseRequest request) {

      VirtualField<CouchbaseRequest, CouchbaseRequestInfo> virtualField =
          VirtualField.find(CouchbaseRequest.class, CouchbaseRequestInfo.class);
      CouchbaseRequestInfo requestInfo = virtualField.get(request);
      if (requestInfo != null) {
        return;
      }

      Context currentContext = Java8BytecodeBridge.currentContext();
      requestInfo = CouchbaseRequestInfo.get(currentContext);
      if (requestInfo != null) {
        // The scope from the initial rxJava subscribe is not available to the networking layer
        // To transfer the request info it is added to the context store
        virtualField.set(request, requestInfo);

        requestInfo.setOperationId(request.operationId());
      }
    }
  }
}
