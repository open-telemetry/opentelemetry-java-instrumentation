/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_6;

import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.extendsClass;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.ClassLoaderMatcher.hasClassesNamed;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.couchbase.client.core.message.CouchbaseRequest;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.api.tracer.utils.NetPeerUtils;
import io.opentelemetry.javaagent.instrumentation.api.ContextStore;
import io.opentelemetry.javaagent.instrumentation.api.InstrumentationContext;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class CouchbaseNetworkInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("com.couchbase.client.core.endpoint.AbstractGenericHandler");
  }

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    // Exact class because private fields are used
    return nameStartsWith("com.couchbase.client.")
        .<TypeDescription>and(
            extendsClass(named("com.couchbase.client.core.endpoint.AbstractGenericHandler")));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    // encode(ChannelHandlerContext ctx, REQUEST msg, List<Object> out)
    return singletonMap(
        isMethod()
            .and(named("encode"))
            .and(takesArguments(3))
            .and(
                takesArgument(
                    0, named("com.couchbase.client.deps.io.netty.channel.ChannelHandlerContext")))
            .and(takesArgument(2, named("java.util.List"))),
        CouchbaseNetworkInstrumentation.class.getName() + "$CouchbaseNetworkAdvice");
  }

  public static class CouchbaseNetworkAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void addNetworkTagsToSpan(
        @Advice.FieldValue("remoteHostname") String remoteHostname,
        @Advice.FieldValue("remoteSocket") String remoteSocket,
        @Advice.FieldValue("localSocket") String localSocket,
        @Advice.Argument(1) CouchbaseRequest request) {
      ContextStore<CouchbaseRequest, Span> contextStore =
          InstrumentationContext.get(CouchbaseRequest.class, Span.class);

      Span span = contextStore.get(request);
      if (span != null) {
        NetPeerUtils.INSTANCE.setNetPeer(span, remoteHostname, null);

        if (remoteSocket != null) {
          int splitIndex = remoteSocket.lastIndexOf(":");
          if (splitIndex != -1) {
            span.setAttribute(
                SemanticAttributes.NET_PEER_PORT,
                (long) Integer.parseInt(remoteSocket.substring(splitIndex + 1)));
          }
        }

        if (CouchbaseConfig.CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
          span.setAttribute("couchbase.local.address", localSocket);
        }
      }
    }
  }
}
