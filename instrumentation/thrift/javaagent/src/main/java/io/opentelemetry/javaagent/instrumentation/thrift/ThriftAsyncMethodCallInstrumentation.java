package io.opentelemetry.javaagent.instrumentation.thrift;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.thrift.async.AsyncMethodCallback;
import org.apache.thrift.async.TAsyncMethodCall;
import org.apache.thrift.protocol.TProtocol;
import java.lang.reflect.Field;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.extendsClass;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public class ThriftAsyncMethodCallInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {

    return extendsClass(named("org.apache.thrift.async.TAsyncMethodCall"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod().and(named("write_args")).and(takesArguments(1)),
        ThriftAsyncMethodCallInstrumentation.class.getName() + "$methodAdvice");
    transformer.applyAdviceToMethod(isConstructor(),
        ThriftAsyncMethodCallInstrumentation.class.getName() + "$ConstructorAdvice");
  }

  @SuppressWarnings("unused")
  public static class methodAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.Argument(0) TProtocol protocol,
        @Advice.This TAsyncMethodCall methodCall
    )  {
      try {
        System.out.println(protocol.getClass().toString());
        if (protocol instanceof ClientOutProtocolWrapper) {
          ((ClientOutProtocolWrapper) protocol).request.methodName = methodCall.getClass()
              .getSimpleName();
          for (Field f : methodCall.getClass().getDeclaredFields()) {
            f.setAccessible(true);
            Object tmp = f.get(methodCall);
            ((ClientOutProtocolWrapper) protocol).request.addArgs("arg_"+f.getName(), tmp.toString());
          }
        }
      }catch (Throwable e){
        System.out.println(e.toString());
      }
    }

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void methodExit(
        @Advice.Argument(0) TProtocol protocol,
        @Advice.This TAsyncMethodCall methodCall
    )  {
      try {
        System.out.println(protocol.getClass().toString());
        if (protocol instanceof ClientOutProtocolWrapper) {
          Field field = TAsyncMethodCall.class.getDeclaredField("callback");
          field.setAccessible(true);
          AsyncMethodCallback callback = (AsyncMethodCallback)field.get(methodCall);
          if(callback instanceof AsyncMethodCallbackWrapper){
            ((AsyncMethodCallbackWrapper)callback).context = ((ClientOutProtocolWrapper)protocol).context;
            ((AsyncMethodCallbackWrapper)callback).request = ((ClientOutProtocolWrapper)protocol).request;
          }

        }
      }catch (Throwable e){
        System.out.println(e.toString());
      }
    }

  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
          @Advice.This TAsyncMethodCall methodCall
    ) throws NoSuchFieldException, IllegalAccessException {
          Field field = TAsyncMethodCall.class.getDeclaredField("callback");
          field.setAccessible(true);
          AsyncMethodCallback callback = (AsyncMethodCallback)field.get(methodCall);
          try {
            if(callback instanceof AsyncMethodCallbackWrapper){
              return;
            }
            AsyncMethodCallbackWrapper asyncMethodCallback = new AsyncMethodCallbackWrapper<Object>(callback);
            field.set(methodCall,asyncMethodCallback);
          } catch (Throwable e) {
            System.out.println(e.toString());
          }
    }
  }
}
