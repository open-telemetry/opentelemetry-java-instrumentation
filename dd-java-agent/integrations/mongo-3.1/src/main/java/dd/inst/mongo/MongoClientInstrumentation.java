package dd.inst.mongo;

import static dd.trace.ExceptionHandlers.defaultExceptionHandler;
import static net.bytebuddy.matcher.ElementMatchers.*;

import com.datadoghq.agent.integration.DDTracingCommandListener;
import com.google.auto.service.AutoService;
import com.mongodb.MongoClientOptions;
import dd.trace.Instrumenter;
import io.opentracing.util.GlobalTracer;
import java.lang.reflect.Modifier;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;

@AutoService(Instrumenter.class)
public final class MongoClientInstrumentation implements Instrumenter {

  @Override
  public AgentBuilder instrument(AgentBuilder agentBuilder) {
    return agentBuilder
        .type(
            named("com.mongodb.MongoClientOptions$Builder")
                .and(
                    declaresMethod(
                        named("addCommandListener")
                            .and(isPublic())
                            .and(
                                takesArguments(
                                    new TypeDescription.Latent(
                                        "com.mongodb.event.CommandListener",
                                        Modifier.PUBLIC,
                                        null,
                                        new TypeDescription.Generic[] {}))))))
        .transform(
            new AgentBuilder.Transformer.ForAdvice()
                .advice(
                    isMethod().and(isPublic()).and(named("build")).and(takesArguments(0)),
                    MongoClientAdvice.class.getName())
                .withExceptionHandler(defaultExceptionHandler()))
        .asDecorator();
  }

  public static class MongoClientAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void injectTraceListener(@Advice.This final Object dis) {
      // referencing "this" in the method args causes the class to load under a transformer.
      // This bypasses the Builder instrumentation. Casting as a workaround.
      MongoClientOptions.Builder builder = (MongoClientOptions.Builder) dis;
      final DDTracingCommandListener listener = new DDTracingCommandListener(GlobalTracer.get());
      builder.addCommandListener(listener);
    }
  }
}
