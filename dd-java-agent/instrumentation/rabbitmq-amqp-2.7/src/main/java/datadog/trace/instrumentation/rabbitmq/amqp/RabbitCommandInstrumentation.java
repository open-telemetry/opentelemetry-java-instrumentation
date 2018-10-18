package datadog.trace.instrumentation.rabbitmq.amqp;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.google.auto.service.AutoService;
import com.rabbitmq.client.Command;
import com.rabbitmq.client.Method;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.api.DDTags;
import io.opentracing.Span;
import io.opentracing.util.GlobalTracer;
import java.util.Collections;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public class RabbitCommandInstrumentation extends Instrumenter.Default {

  public RabbitCommandInstrumentation() {
    super("amqp", "rabbitmq");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return not(isInterface()).and(safeHasSuperType(named("com.rabbitmq.client.Command")));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      // These are only used by muzzleCheck.
      packageName + ".TextMapExtractAdapter", packageName + ".TracedDelegatingConsumer",
    };
  }

  @Override
  public Map<? extends ElementMatcher, String> transformers() {
    return Collections.singletonMap(isConstructor(), CommandConstructorAdvice.class.getName());
  }

  public static class CommandConstructorAdvice {
    @Advice.OnMethodExit
    public static void setResourceNameAddHeaders(@Advice.This final Command command) {
      final Span span = GlobalTracer.get().activeSpan();

      final Method method = command.getMethod();
      if (span != null && method != null) {
        final String name = method.protocolMethodName();
        span.setTag(DDTags.RESOURCE_NAME, name);
        span.setTag("amqp.command", name);
      }
    }

    /**
     * This instrumentation will match with 2.6, but the channel instrumentation only matches with
     * 2.7 because of TracedDelegatingConsumer. This unused method is added to ensure consistent
     * muzzle validation by preventing match with 2.6.
     */
    public static void muzzleCheck(final TracedDelegatingConsumer consumer) {
      consumer.handleRecoverOk(null);
    }
  }
}
