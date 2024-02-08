package io.opentelemetry.javaagent.instrumentation.nifi.v1_24_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.nifi.processor.ProcessSession;

public final class CommitSession implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("org.apache.nifi.processor.ProcessSession");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return hasSuperType(named("org.apache.nifi.processor.ProcessSession"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(named("commit"), this.getClass().getName() + "$CommitSessionAdvice");
  }

  @SuppressWarnings("unused")
  public static class CommitSessionAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.This ProcessSession processSession) {

      System.out.println(">>>>>> DEBUG : IN CommitSessionAdvice - ProcessSession.toString() = : " + processSession.toString());

      //Issue when trying to reference 'BinFiles' : To reproduce uncomment line below
      //System.out.println(">>>>>>> DEBUG : BinFiles " + org.apache.nifi.processor.util.bin.BinFiles.class.getName());
    }
  }
}