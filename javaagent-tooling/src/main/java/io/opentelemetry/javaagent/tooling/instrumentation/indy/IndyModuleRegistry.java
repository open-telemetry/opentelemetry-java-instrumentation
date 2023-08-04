package io.opentelemetry.javaagent.tooling.instrumentation.indy;

import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class IndyModuleRegistry {

  private final ConcurrentHashMap<String, InstrumentationModule> adviceNameToModule = new ConcurrentHashMap<>();

  public InstrumentationModuleClassLoader getInstrumentationClassloader(String adviceClassName, ClassLoader instrumentedClassloader) {
    return null; //TODO implement
  }

  public void registerIndyModule(InstrumentationModule module) {
    if(!module.isIndyModule()) {
      throw new IllegalArgumentException("Provided module is not an indy module!");
    }
    Set<String> adviceClassNames = getModuleAdviceNames(module);
    Optional<String> conflict = adviceClassNames.stream()
        .filter(adviceNameToModule::containsKey)
        .findFirst();
    if(conflict.isPresent()) {
      throw new IllegalArgumentException("Advice "+conflict+" is already registered!");
    }
    for(String advice : adviceClassNames) {
      adviceNameToModule.put(advice, module);
    }
  }


  private Set<String> getModuleAdviceNames(InstrumentationModule module) {
    Set<String> adviceNames = new HashSet<>();
    TypeTransformer nameCollector = new TypeTransformer() {
      @Override
      public void applyAdviceToMethod(ElementMatcher<? super MethodDescription> methodMatcher,
          String adviceClassName) {
        adviceNames.add(adviceClassName);
      }

      @Override
      public void applyTransformer(AgentBuilder.Transformer transformer) {}
    };
    for(TypeInstrumentation instr : module.typeInstrumentations()) {
      instr.transform(nameCollector);
    }
    return adviceNames;
  }
}
