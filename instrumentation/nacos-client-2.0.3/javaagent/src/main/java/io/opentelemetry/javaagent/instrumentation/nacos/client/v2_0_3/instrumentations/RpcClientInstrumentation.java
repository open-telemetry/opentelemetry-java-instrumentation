package io.opentelemetry.javaagent.instrumentation.nacos.client.v2_0_3.instrumentations;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isProtected;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import com.alibaba.nacos.api.remote.response.Response;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.nacos.client.v2_0_3.advices.RpcClientHandleServerRequestAdvice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

public class RpcClientInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return ElementMatchers.named("com.alibaba.nacos.common.remote.client.RpcClient");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isProtected())
            .and(namedOneOf("handleServerRequest"))
            .and(returns(Response.class)),
        RpcClientHandleServerRequestAdvice.class.getName()
    );
  }

}
