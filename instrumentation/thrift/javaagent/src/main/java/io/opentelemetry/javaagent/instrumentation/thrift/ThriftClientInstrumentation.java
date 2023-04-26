package io.opentelemetry.javaagent.instrumentation.thrift;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.thrift.TBase;
import org.apache.thrift.TServiceClient;
import org.apache.thrift.protocol.TProtocol;

import java.lang.reflect.Field;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.extendsClass;
import static io.opentelemetry.javaagent.instrumentation.thrift.ThriftSingletons.clientInstrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;


public class ThriftClientInstrumentation implements TypeInstrumentation{
  @Override
  public ElementMatcher<TypeDescription> typeMatcher(){
    return extendsClass(named("org.apache.thrift.TServiceClient"));
  }
  @Override
  public void transform(TypeTransformer transformer){
    transformer.applyAdviceToMethod(
        isMethod().and(named("sendBase")),ThriftClientInstrumentation.class.getName() + "$ClientSendAdvice"
    );
    transformer.applyAdviceToMethod(
        isMethod().and(named("receiveBase")),
        ThriftClientInstrumentation.class.getName() + "$ClientReceiveAdvice");
    transformer.applyAdviceToMethod(isConstructor().and(takesArguments(1)),ThriftClientInstrumentation.class.getName() + "$ConstructorAdvice");
  }

  public static class ClientSendAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.This TServiceClient client,
        @Advice.Argument(0) String methodName,
        @Advice.Argument(1) TBase args
    ) throws NoSuchFieldException, IllegalAccessException {
      Field field = TServiceClient.class.getDeclaredField("oprot_");
      field.setAccessible(true);
      int i = 1;
      ClientOutProtocolWrapper tmp = (ClientOutProtocolWrapper)field.get(client);
      while(true){
        if(args.fieldForId(i)!=null){
          try{
            tmp.request.addArgs("arg_"+args.fieldForId(i),args.getFieldValue(args.fieldForId(i)).toString());
            i++;
          }catch (Throwable e){
            System.out.println(e.toString());
            break;
          }
        }else{
          break;
        }
      }
      tmp.request.methodName = methodName;
    }

  }

  @SuppressWarnings("unused")
  public static class ClientReceiveAdvice {

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void methodExit(
      @Advice.This TServiceClient client
      ) throws NoSuchFieldException, IllegalAccessException {
    Field field = TServiceClient.class.getDeclaredField("oprot_");
    field.setAccessible(true);
    ClientOutProtocolWrapper tmp = (ClientOutProtocolWrapper)field.get(client);
    if (tmp.context!=null){
      clientInstrumenter().end(tmp.context,tmp.request,0,null);
    }

  }
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.This TServiceClient client,
        @Advice.Argument(0) TProtocol protocol

    ) throws NoSuchFieldException {
      try{
        Field field = TServiceClient.class.getDeclaredField("oprot_");
        field.setAccessible(true);
        field.set(client,new ClientOutProtocolWrapper(protocol));
      }
      catch (Throwable e){
        System.out.println(e.toString());
      }
    }
  }
}
