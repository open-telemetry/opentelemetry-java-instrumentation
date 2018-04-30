package datadog.trace.instrumentation.ratpack;

import static datadog.trace.instrumentation.ratpack.RatpackInstrumentation.ROOT_RATPACK_HELPER_INJECTOR;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.DDAdvice;
import datadog.trace.agent.tooling.HelperInjector;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.instrumentation.ratpack.impl.RatpackHttpClientAdvice;
import java.net.URI;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;

@AutoService(Instrumenter.class)
public final class RatpackHttpClientInstrumentation extends Instrumenter.Configurable {

  private static final HelperInjector HTTP_CLIENT_HELPER_INJECTOR =
      new HelperInjector(
          "datadog.trace.instrumentation.ratpack.impl.RatpackHttpClientAdvice$RatpackHttpClientRequestAdvice",
          "datadog.trace.instrumentation.ratpack.impl.RatpackHttpClientAdvice$RatpackHttpClientRequestStreamAdvice",
          "datadog.trace.instrumentation.ratpack.impl.RatpackHttpClientAdvice$RatpackHttpGetAdvice",
          "datadog.trace.instrumentation.ratpack.impl.RatpackHttpClientAdvice$RequestAction",
          "datadog.trace.instrumentation.ratpack.impl.RatpackHttpClientAdvice$ResponseAction",
          "datadog.trace.instrumentation.ratpack.impl.RatpackHttpClientAdvice$StreamedResponseAction",
          "datadog.trace.instrumentation.ratpack.impl.RequestSpecInjectAdapter",
          "datadog.trace.instrumentation.ratpack.impl.WrappedRequestSpec");
  public static final TypeDescription.ForLoadedType URI_TYPE_DESCRIPTION =
      new TypeDescription.ForLoadedType(URI.class);

  public RatpackHttpClientInstrumentation() {
    super(RatpackInstrumentation.EXEC_NAME);
  }

  @Override
  protected boolean defaultEnabled() {
    return false;
  }

  @Override
  public AgentBuilder apply(final AgentBuilder agentBuilder) {

    return agentBuilder
        .type(
            not(isInterface()).and(hasSuperType(named("ratpack.http.client.HttpClient"))),
            RatpackInstrumentation.CLASSLOADER_CONTAINS_RATPACK_1_4_OR_ABOVE)
        .transform(ROOT_RATPACK_HELPER_INJECTOR)
        .transform(HTTP_CLIENT_HELPER_INJECTOR)
        .transform(
            DDAdvice.create()
                .advice(
                    named("request")
                        .and(
                            takesArguments(
                                URI_TYPE_DESCRIPTION,
                                RatpackInstrumentation.ACTION_TYPE_DESCRIPTION)),
                    RatpackHttpClientAdvice.RatpackHttpClientRequestAdvice.class.getName()))
        .transform(
            DDAdvice.create()
                .advice(
                    named("requestStream")
                        .and(
                            takesArguments(
                                URI_TYPE_DESCRIPTION,
                                RatpackInstrumentation.ACTION_TYPE_DESCRIPTION)),
                    RatpackHttpClientAdvice.RatpackHttpClientRequestStreamAdvice.class.getName()))
        .transform(
            DDAdvice.create()
                .advice(
                    named("get")
                        .and(
                            takesArguments(
                                URI_TYPE_DESCRIPTION,
                                RatpackInstrumentation.ACTION_TYPE_DESCRIPTION)),
                    RatpackHttpClientAdvice.RatpackHttpGetAdvice.class.getName()))
        .asDecorator();
  }
}
