package io.opentelemetry.javaagent.instrumentation.thrift;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.thrift.async.TAsyncClient;
import org.apache.thrift.protocol.TProtocolFactory;
import java.lang.reflect.Field;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.extendsClass;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
//import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public class ThriftAsyncClientInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher(){
    return extendsClass(named("org.apache.thrift.async.TAsyncClient"));
  }
  @Override
  public void transform(TypeTransformer transformer){
    transformer.applyAdviceToMethod(
        isMethod(),
        ThriftAsyncClientInstrumentation.class.getName() + "$ClientAdvice");
    transformer.applyAdviceToMethod(isConstructor(),ThriftAsyncClientInstrumentation.class.getName() + "$ConstructorAdvice");
  }

  @SuppressWarnings("unused")
  public static class ClientAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodExit(
    ){
      return;
    }
  }


  public static class ConstructorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.This TAsyncClient client,
        @Advice.FieldValue("___protocolFactory") TProtocolFactory inputTProtocolFactory
    ) throws NoSuchFieldException {
      try{
        Field field = TAsyncClient.class.getDeclaredField("___protocolFactory");
        field.setAccessible(true);
        ClientProtocolFactoryWrapper factoryWrapper = new ClientProtocolFactoryWrapper(inputTProtocolFactory);
        field.set(client,factoryWrapper);
      }
      catch (Throwable e){
        System.out.println(e.toString());
      }
    }
  }
}
