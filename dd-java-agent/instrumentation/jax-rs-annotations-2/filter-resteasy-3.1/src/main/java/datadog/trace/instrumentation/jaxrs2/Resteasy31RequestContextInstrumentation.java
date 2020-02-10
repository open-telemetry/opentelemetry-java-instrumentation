package datadog.trace.instrumentation.jaxrs2;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.bootstrap.instrumentation.api.AgentScope;
import java.lang.reflect.Method;
import javax.ws.rs.container.ContainerRequestContext;
import net.bytebuddy.asm.Advice;
import org.jboss.resteasy.core.ResourceMethodInvoker;
import org.jboss.resteasy.core.interception.jaxrs.PostMatchContainerRequestContext;

/**
 * RESTEasy specific context instrumentation.
 *
 * <p>JAX-RS does not define a way to get the matched resource method from the <code>
 * ContainerRequestContext</code>
 *
 * <p>In the RESTEasy implementation, <code>ContainerRequestContext</code> is implemented by <code>
 * PostMatchContainerRequestContext</code>. This class provides a way to get the matched resource
 * method through <code>getResourceMethod()</code>.
 */
@AutoService(Instrumenter.class)
public class Resteasy31RequestContextInstrumentation extends AbstractRequestContextInstrumentation {
  public static class ContainerRequestContextAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AgentScope decorateAbortSpan(@Advice.This final ContainerRequestContext context) {
      if (context.getProperty(JaxRsAnnotationsDecorator.ABORT_HANDLED) == null
          && context instanceof PostMatchContainerRequestContext) {

        final ResourceMethodInvoker resourceMethodInvoker =
            ((PostMatchContainerRequestContext) context).getResourceMethod();
        final Method method = resourceMethodInvoker.getMethod();
        final Class resourceClass = resourceMethodInvoker.getResourceClass();

        return RequestFilterHelper.createOrUpdateAbortSpan(context, resourceClass, method);
      }

      return null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Enter final AgentScope scope, @Advice.Thrown final Throwable throwable) {
      RequestFilterHelper.closeSpanAndScope(scope, throwable);
    }
  }
}
