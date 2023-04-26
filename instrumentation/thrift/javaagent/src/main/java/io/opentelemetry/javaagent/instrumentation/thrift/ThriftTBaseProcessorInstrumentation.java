package io.opentelemetry.javaagent.instrumentation.thrift;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import org.apache.thrift.ProcessFunction;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;


import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.extendsClass;
import static io.opentelemetry.javaagent.instrumentation.thrift.ThriftSingletons.serverInstrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;

public class ThriftTBaseProcessorInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return extendsClass(named("org.apache.thrift.ProcessFunction"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod().and(named("process")),
        ThriftTBaseProcessorInstrumentation.class.getName() + "$ProcessAdvice");
  }

  public static class ProcessAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.Argument(1) TProtocol inpot_,
        @Advice.This ProcessFunction processFunction){
      if(inpot_ instanceof ServerInProtocolWrapper){
        TTransport transport_ = ((ServerInProtocolWrapper) inpot_).getRTransport();
        if(transport_ instanceof TSocket){
          ((ServerInProtocolWrapper) inpot_).request.host = ((TSocket) transport_).getSocket().getInetAddress().toString();
          ((ServerInProtocolWrapper) inpot_).request.port = ((TSocket) transport_).getSocket().getPort();
        }
        ((ServerInProtocolWrapper) inpot_).request.methodName = processFunction.getMethodName();
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Argument(1) TProtocol inpot_,
        @Advice.This ProcessFunction processFunction
    ) {
      if(inpot_ instanceof ServerInProtocolWrapper){
        ((ServerInProtocolWrapper) inpot_).request.methodName = processFunction.getMethodName();
        Context context = ((ServerInProtocolWrapper) inpot_).context;
        ThriftRequest request = ((ServerInProtocolWrapper) inpot_).request;
        serverInstrumenter().end(context, request, 0, null);
        ((ServerInProtocolWrapper) inpot_).scope.close();
      }
    }
  }
}

