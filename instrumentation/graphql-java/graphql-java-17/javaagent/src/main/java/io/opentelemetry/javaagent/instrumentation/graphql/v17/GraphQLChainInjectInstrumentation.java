package io.opentelemetry.javaagent.instrumentation.graphql.v17;

import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import graphql.GraphQL;
import graphql.execution.instrumentation.ChainedInstrumentation;
import graphql.execution.instrumentation.Instrumentation;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.graphql.v17.GraphQLRealInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Injects the {@link GraphQLRealInstrumentation} into every {@link GraphQL} object. This {@code
 * TypeInstrumentation} calls the {@link GraphQL.Builder#instrumentation(Instrumentation)} method
 * just after entering {@link GraphQL.Builder#build()}. When an {@code Instrumentation} has already
 * been provided, the existing {@code Instrumentation} is chained using {@link
 * ChainedInstrumentation}.
 */
public class GraphQLChainInjectInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("graphql.GraphQL$Builder");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isPublic()
            .and(named("build"))
            .and(takesArguments(0))
            .and(returns(named("graphql.GraphQL"))),
        this.getClass().getName() + "$MethodAdvice");
  }

  @SuppressWarnings("unused")
  public static class MethodAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.This Object ourBuilder, @Advice.FieldValue Object instrumentation) {
      // Check if we are really in the right type of object
      if (ourBuilder instanceof GraphQL.Builder) {
        // The instrumentation defined by the user may be null
        final Instrumentation existingInstrumentation =
            instrumentation instanceof Instrumentation ? (Instrumentation) instrumentation : null;
        // Create the OpenTelemetry-specific GraphQL Java instrumentation
        // TODO Should this really be GlobalOpenTelemetry?
        final Instrumentation oTelInstrumentation =
            new GraphQLRealInstrumentation(GlobalOpenTelemetry.get());
        // If the user has previously injected instrumentation, we need to chain it.
        //  Make sure we are at the front of the chain though! If we are at the back of
        //   the chain, we may not pick up performance bottlenecks, or changes that affect execution
        //   introduced by other instrumentation.
        final Instrumentation newInstrumentation =
            (existingInstrumentation == null)
                ? oTelInstrumentation
                : new ChainedInstrumentation(oTelInstrumentation, existingInstrumentation);
        // Now we cast `this` to our builder and inject the new instrumentation.
        //  Please note that we do this at the start of the #build method, directly after entering.
        //  Therefore, future changes in the graphql.GraphQL$Builder#build method that may freeze
        //   modification (of the builder) via the #instrumentation method can not affect us.
        final GraphQL.Builder theBuilder = (GraphQL.Builder) ourBuilder;
        theBuilder.instrumentation(newInstrumentation);
      }
    }
  }
}
