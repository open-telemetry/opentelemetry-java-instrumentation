/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_6;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.extendsClass;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.couchbase.client.core.message.CouchbaseRequest;
import io.opentelemetry.instrumentation.api.field.VirtualField;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.couchbase.v2_0.CouchbaseRequestInfo;
import java.util.List;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class CouchbaseNetworkInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("com.couchbase.client.core.endpoint.AbstractGenericHandler");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    // Exact class because private fields are used
    return nameStartsWith("com.couchbase.client.")
        .and(extendsClass(named("com.couchbase.client.core.endpoint.AbstractGenericHandler")));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    // encode(ChannelHandlerContext ctx, REQUEST msg, List<Object> out)
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("encode"))
            .and(takesArguments(3))
            .and(
                takesArgument(
                    0, named("com.couchbase.client.deps.io.netty.channel.ChannelHandlerContext")))
            .and(takesArgument(2, List.class)),
        CouchbaseNetworkInstrumentation.class.getName() + "$CouchbaseNetworkAdvice");
  }

  @SuppressWarnings("unused")
  public static class CouchbaseNetworkAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void addNetworkTagsToSpan(
        @Advice.FieldValue("remoteHostname") String remoteHostname,
        @Advice.FieldValue("remoteSocket") String remoteSocket,
        @Advice.FieldValue("localSocket") String localSocket,
        @Advice.Argument(1) CouchbaseRequest request) {
      VirtualField<CouchbaseRequest, CouchbaseRequestInfo> virtualField =
          VirtualField.find(CouchbaseRequest.class, CouchbaseRequestInfo.class);

      CouchbaseRequestInfo requestInfo = virtualField.get(request);
      if (requestInfo != null) {
        if (remoteHostname != null) {
          requestInfo.setPeerName(remoteHostname);
        }

        if (remoteSocket != null) {
          int splitIndex = remoteSocket.lastIndexOf(":");
          if (splitIndex != -1) {
            requestInfo.setPeerPort(Integer.parseInt(remoteSocket.substring(splitIndex + 1)));
          }
        }

        requestInfo.setLocalAddress(localSocket);
      }
    }
  }
}
