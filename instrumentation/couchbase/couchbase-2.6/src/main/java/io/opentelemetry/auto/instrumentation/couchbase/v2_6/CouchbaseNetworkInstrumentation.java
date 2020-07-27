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

package io.opentelemetry.auto.instrumentation.couchbase.v2_6;

import static io.opentelemetry.auto.tooling.ClassLoaderMatcher.hasClassesNamed;
import static io.opentelemetry.auto.tooling.bytebuddy.matcher.AgentElementMatchers.extendsClass;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.couchbase.client.core.message.CouchbaseRequest;
import com.couchbase.client.java.transcoder.crypto.JsonCryptoTranscoder;
import com.google.auto.service.AutoService;
import io.opentelemetry.auto.bootstrap.ContextStore;
import io.opentelemetry.auto.bootstrap.InstrumentationContext;
import io.opentelemetry.auto.bootstrap.instrumentation.decorator.BaseDecorator;
import io.opentelemetry.auto.bootstrap.instrumentation.jdbc.DbSystem;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.attributes.SemanticAttributes;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public class CouchbaseNetworkInstrumentation extends Instrumenter.Default {
  public CouchbaseNetworkInstrumentation() {
    super(DbSystem.COUCHBASE);
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderMatcher() {
    // Optimization for expensive typeMatcher.
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
  public Map<String, String> contextStore() {
    return singletonMap("com.couchbase.client.core.message.CouchbaseRequest", Span.class.getName());
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
        @Advice.FieldValue("remoteHostname") final String remoteHostname,
        @Advice.FieldValue("remoteSocket") final String remoteSocket,
        @Advice.FieldValue("localSocket") final String localSocket,
        @Advice.Argument(1) final CouchbaseRequest request) {
      ContextStore<CouchbaseRequest, Span> contextStore =
          InstrumentationContext.get(CouchbaseRequest.class, Span.class);

      Span span = contextStore.get(request);
      if (span != null) {
        BaseDecorator.setPeer(span, remoteHostname, null);

        if (remoteSocket != null) {
          int splitIndex = remoteSocket.lastIndexOf(":");
          if (splitIndex != -1) {
            span.setAttribute(
                SemanticAttributes.NET_PEER_PORT.key(),
                Integer.parseInt(remoteSocket.substring(splitIndex + 1)));
          }
        }

        span.setAttribute("local.address", localSocket);
      }
    }

    // 2.6.0 and above
    public static void muzzleCheck(final JsonCryptoTranscoder transcoder) {
      transcoder.documentType();
    }
  }
}
