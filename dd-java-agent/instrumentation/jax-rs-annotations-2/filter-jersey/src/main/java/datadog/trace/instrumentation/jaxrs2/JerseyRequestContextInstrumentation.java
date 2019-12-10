package datadog.trace.instrumentation.jaxrs2;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.instrumentation.api.AgentScope;
import java.lang.reflect.Method;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.UriInfo;
import net.bytebuddy.asm.Advice;

/**
 * Jersey specific context instrumentation.
 *
 * <p>JAX-RS does not define a way to get the matched resource method from the <code>
 * ContainerRequestContext</code>
 *
 * <p>In the Jersey implementation, <code>UriInfo</code> implements <code>ResourceInfo</code>. The
 * matched resource method can be retrieved from that object
 */
@AutoService(Instrumenter.class)
public class JerseyRequestContextInstrumentation extends AbstractRequestContextInstrumentation {
  public static class ContainerRequestContextAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AgentScope decorateAbortSpan(@Advice.This final ContainerRequestContext context) {
      final UriInfo uriInfo = context.getUriInfo();

      if (context.getProperty(JaxRsAnnotationsDecorator.ABORT_HANDLED) == null
          && uriInfo instanceof ResourceInfo) {

        final ResourceInfo resourceInfo = (ResourceInfo) uriInfo;
        final Method method = resourceInfo.getResourceMethod();
        final Class resourceClass = resourceInfo.getResourceClass();

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
