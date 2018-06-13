package datadog.trace.instrumentation.lettuce;

import datadog.trace.api.DDTags;
import io.lettuce.core.ConnectionFuture;
import io.lettuce.core.RedisURI;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.util.Collections;
import net.bytebuddy.asm.Advice;

public class ConnectionFutureAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static Scope startSpan(@Advice.Argument(1) final RedisURI redisURI) {
    final Scope scope = GlobalTracer.get().buildSpan("redis.query").startActive(false);

    final Span span = scope.span();
    Tags.DB_TYPE.set(span, LettuceInstrumentationUtil.SERVICE_NAME);
    Tags.SPAN_KIND.set(span, Tags.SPAN_KIND_CLIENT);
    Tags.COMPONENT.set(span, LettuceInstrumentationUtil.COMPONENT_NAME);

    final int redisPort = redisURI.getPort();
    Tags.PEER_PORT.set(span, redisPort);
    final String redisHost = redisURI.getHost();
    Tags.PEER_HOSTNAME.set(span, redisHost);

    final String url = redisHost + ":" + redisPort + "/" + redisURI.getDatabase();
    span.setTag("db.redis.url", url);
    span.setTag("db.redis.dbIndex", redisURI.getDatabase());
    span.setTag(DDTags.RESOURCE_NAME, "CONNECT:" + url);
    span.setTag(DDTags.SERVICE_NAME, LettuceInstrumentationUtil.SERVICE_NAME);
    span.setTag(DDTags.SPAN_TYPE, LettuceInstrumentationUtil.SERVICE_NAME);

    return scope;
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Enter final Scope scope,
      @Advice.Thrown final Throwable throwable,
      @Advice.Return final ConnectionFuture connectionFuture) {
    if (throwable != null) {
      final Span span = scope.span();
      Tags.ERROR.set(span, true);
      span.log(Collections.singletonMap("error.object", throwable));
      scope.close();
      return;
    }

    // close spans on error or normal completion
    connectionFuture.handleAsync(new LettuceAsyncBiFunction<>(scope.span()));
    scope.close();
  }
}
