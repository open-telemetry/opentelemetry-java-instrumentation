package datadog.trace.instrumentation.jaxrs;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import java.util.HashMap;
import java.util.Map;
import javax.ws.rs.client.ClientBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class JaxRsClientInstrumentation extends Instrumenter.Default {

  public JaxRsClientInstrumentation() {
    super("jax-rs", "jaxrs", "jax-rs-client");
  }

  @Override
  public ElementMatcher typeMatcher() {
    return safeHasSuperType(named("javax.ws.rs.client.ClientBuilder"));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "datadog.trace.instrumentation.jaxrs.ClientTracingFeature",
      "datadog.trace.instrumentation.jaxrs.ClientTracingFilter",
      "datadog.trace.instrumentation.jaxrs.InjectAdapter"
    };
  }

  @Override
  public Map<ElementMatcher, String> transformers() {
    final Map<ElementMatcher, String> transformers = new HashMap<>();
    transformers.put(
        named("build").and(returns(safeHasSuperType(named("javax.ws.rs.client.Client")))),
        ClientBuilderAdvice.class.getName());
    return transformers;
  }

  public static class ClientBuilderAdvice {

    @Advice.OnMethodEnter
    public static void registerFeature(@Advice.This final ClientBuilder builder) {
      builder.register(ClientTracingFeature.class);
    }
  }
}
