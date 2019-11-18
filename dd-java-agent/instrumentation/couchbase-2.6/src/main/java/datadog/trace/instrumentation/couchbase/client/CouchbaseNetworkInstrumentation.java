package datadog.trace.instrumentation.couchbase.client;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.couchbase.client.core.message.CouchbaseRequest;
import com.couchbase.client.java.transcoder.crypto.JsonCryptoTranscoder;
import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.bootstrap.ContextStore;
import datadog.trace.bootstrap.InstrumentationContext;
import datadog.trace.instrumentation.api.AgentSpan;
import datadog.trace.instrumentation.api.Tags;
import java.util.Collections;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public class CouchbaseNetworkInstrumentation extends Instrumenter.Default {
  public CouchbaseNetworkInstrumentation() {
    super("couchbase");
  }

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    // Exact class because private fields are used
    return safeHasSuperType(named("com.couchbase.client.core.endpoint.AbstractGenericHandler"));
  }

  @Override
  public Map<String, String> contextStore() {
    return Collections.singletonMap(
        "com.couchbase.client.core.message.CouchbaseRequest", AgentSpan.class.getName());
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
      final ContextStore<CouchbaseRequest, AgentSpan> contextStore =
          InstrumentationContext.get(CouchbaseRequest.class, AgentSpan.class);

      final AgentSpan span = contextStore.get(request);
      if (span != null) {
        span.setTag(Tags.PEER_HOSTNAME, remoteHostname);

        if (remoteSocket != null) {
          final int splitIndex = remoteSocket.lastIndexOf(":");
          if (splitIndex != -1) {
            span.setTag(Tags.PEER_PORT, Integer.parseInt(remoteSocket.substring(splitIndex + 1)));
          }
        }

        span.setTag("local.address", localSocket);
      }
    }

    // 2.6.0 and above
    public static void muzzleCheck(final JsonCryptoTranscoder transcoder) {
      transcoder.documentType();
    }
  }
}
