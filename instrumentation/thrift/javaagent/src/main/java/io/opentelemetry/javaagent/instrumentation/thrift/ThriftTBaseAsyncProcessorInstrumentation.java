package io.opentelemetry.javaagent.instrumentation.thrift;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.server.AbstractNonblockingServer;
import org.apache.thrift.transport.TNonblockingSocket;
import java.lang.reflect.Field;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.extendsClass;
import static io.opentelemetry.javaagent.instrumentation.thrift.ThriftSingletons.serverInstrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public class ThriftTBaseAsyncProcessorInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return extendsClass(named("org.apache.thrift.TBaseAsyncProcessor"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod().and(named("process")).and(takesArguments(1)),
        ThriftTBaseAsyncProcessorInstrumentation.class.getName() + "$ProcessAdvice");
  }

  public static class ProcessAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.Argument(0) AbstractNonblockingServer.AsyncFrameBuffer fb
    ) throws NoSuchFieldException, IllegalAccessException {
      try {
        TProtocol inpot_ = fb.getInputProtocol();
        Field field = AbstractNonblockingServer.FrameBuffer.class.getDeclaredField("trans_");
        field.setAccessible(true);
        TNonblockingSocket trans_ = (TNonblockingSocket) field.get(fb);
        ((ServerInProtocolWrapper) inpot_).request.host = trans_.getSocketChannel().socket().getInetAddress().getHostAddress();
        ((ServerInProtocolWrapper) inpot_).request.port = trans_.getSocketChannel().socket().getPort();
        Field field2 = AbstractNonblockingServer.FrameBuffer.class.getDeclaredField("inProt_");
        field2.setAccessible(true);
        field2.set(fb,inpot_);
      }catch (Throwable e){
        System.out.println(e.toString());
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Argument(0) AbstractNonblockingServer.AsyncFrameBuffer fb
    )  {
      TProtocol inpot_ = fb.getInputProtocol();
      if(inpot_ instanceof ServerInProtocolWrapper){
        Context context = ((ServerInProtocolWrapper) inpot_).context;
        ThriftRequest request = ((ServerInProtocolWrapper) inpot_).request;
        serverInstrumenter().end(context, request, 0, null);
        Thread.dumpStack();
        ((ServerInProtocolWrapper) inpot_).scope.close();
      }
    }
  }
}
