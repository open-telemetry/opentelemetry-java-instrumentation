package io.opentelemetry.javaagent.instrumentation.thrift;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.server.TServer;
import org.apache.thrift.transport.TNonblockingSocket;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import java.lang.reflect.Field;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.extendsClass;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

public class ThriftTSocketInstrumentation  implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher(){
//    return named("com.thrift_demo.test");
    return extendsClass(named("org.apache.thrift.transport.TServerTransport"));
//    return implementsInterface(named("org.apache.thrift.protocol.TProtocolFactory"));
  }
  @Override
  public void transform(TypeTransformer transformer){
    transformer.applyAdviceToMethod(
        isMethod().and(named("accept")),
       ThriftTSocketInstrumentation.class.getName() + "$ServerAdvice");
//    transformer.applyAdviceToMethod(isConstructor(),ThriftTSocketInstrumentation.class.getName() + "$ConstructorAdvice");
  }


  public static class ServerAdvice {
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Return TTransport tNonblockingSocket
    ){
//      try{
////        if(tNonblockingSocket instanceof TNonblockingSocket){
////          System.out.println("Accept");
////          System.out.println(((TNonblockingSocket)tNonblockingSocket).getSocketChannel().socket().getInetAddress());
////          System.out.println(((TNonblockingSocket)tNonblockingSocket).getSocketChannel().socket().getPort());
////          System.out.println(((TNonblockingSocket)tNonblockingSocket).getSocketChannel().socket().getLocalAddress());
////          System.out.println(((TNonblockingSocket)tNonblockingSocket).getSocketChannel().socket().getLocalPort());
////          System.out.println("Accept su");
////        }else if(tNonblockingSocket instanceof TSocket){
////          System.out.println("Accept 2");
////          System.out.println(((TSocket) tNonblockingSocket).getSocket().getInetAddress());
////          System.out.println(((TSocket) tNonblockingSocket).getSocket().getPort());
////          System.out.println(((TSocket) tNonblockingSocket).getSocket().getLocalAddress());
////          System.out.println(((TSocket) tNonblockingSocket).getSocket().getLocalPort());
////          System.out.println("Accept su 2");
////        }
//
//      }catch (Throwable e){
//        System.out.println(e.toString());
//      }


    }
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(

    ) throws NoSuchFieldException {

    }
  }
}
