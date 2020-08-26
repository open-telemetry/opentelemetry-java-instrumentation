# Instrumentation for Java Servlets

## A word about version

We support Servlet API starting from version 2.2.
But various instrumentations apply to different versions of the API.
They are divided into several sub-modules:

`servlet-common` contains instrumentations applicable to all API versions that we support.

`servlet-2.2` contains instrumentations applicable to Servlet API 2.2, but not to to 3+.

`servlet-3/servlet-3-common` contains instrumentations that are applicable to Servlet API 3.0 or newer.

`servlet-3/servlet-3.0` contains instrumentations that require Servlet API 3.0, but lower than 3.1.

`servlet-3/servlet-3.1` contains instrumentations that require Servlet API 3.1 or newer.

## Implementation details

In order to fully understand how java servlet instrumentation work,
let us first take a look at the following stacktrace from Spring PetClinic application.
Unimportant frames are redacted, points of interests are highlighted and discussed below.

<pre>
<b>at org.springframework.samples.petclinic.owner.OwnerController.initCreationForm(OwnerController.java:60)</b>
...
at org.springframework.web.servlet.mvc.method.AbstractHandlerMethodAdapter.handle(AbstractHandlerMethodAdapter.java:87)
at org.springframework.web.servlet.DispatcherServlet.doDispatch(DispatcherServlet.java:1040)
<b>at org.springframework.web.servlet.DispatcherServlet.doService(DispatcherServlet.java:943)</b>
at org.springframework.web.servlet.FrameworkServlet.processRequest(FrameworkServlet.java:1006)
<b>at org.springframework.web.servlet.FrameworkServlet.doGet(FrameworkServlet.java:898)</b>
<b>at javax.servlet.http.HttpServlet.service(HttpServlet.java:634)</b>
<b>at org.springframework.web.servlet.FrameworkServlet.service(FrameworkServlet.java:883)</b>
<b>at javax.servlet.http.HttpServlet.service(HttpServlet.java:741)</b>
...
<b>at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:166)</b>
...
at org.apache.tomcat.util.net.SocketProcessorBase.run(SocketProcessorBase.java:49)
at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1128)
at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:628)
at org.apache.tomcat.util.threads.TaskThread$WrappingRunnable.run(TaskThread.java:61)
at java.base/java.lang.Thread.run(Thread.java:834)
</pre>

Everything starts when HTTP request processing reaches the first class from Servlet specification.
In the example above this is `ApplicationFilterChain.doFilter(ServletRequest, ServletResponse)` method.
Let us call this first servlet specific method an "entry point".
This is the main target for `Servlet3Instrumentation` and `Servlet2Instrumentation` instrumenters:

`public void javax.servlet.FilterChain#doFilter(ServletRequest, ServletResponse)`

`public void javax.servlet.http.HttpServlet#service(ServletRequest, ServletResponse)`.

For example, Jetty Servlet container does not have default filter chain and in many cases will have
the second method as instrumentation entry point.
These instrumentations are located in two separate submodules `request-3.0` and `request-2.3`, respectively,
because they and corresponding tests depend on different versions of servlet specification.

Next, request passes several other methods from Servlet specification, such as

`protected void javax.servlet.http.HttpServlet#service(HttpServletRequest, HttpServletResponse)` or

`protected void org.springframework.web.servlet.FrameworkServlet#doGet(HttpServletRequest, HttpServletResponse)`.

They are the targets for `HttpServletInstrumentation`.
From the observability point of view nothing of interest usually happens inside these methods.
Thus it usually does not make sense to create spans from them, as they would only add useless noise.
For this reason `HttpServletInstrumentation` is disabled by default.
In rare cases when you need it, you can enable it using configuration property `otel.integration.servlet-service.enabled`.

In exactly the same situation are all other Servlet filters beyond the initial entry point.
Usually unimportant, they may be sometimes of interest during troubleshooting.
They are instrumented by `FilterInstrumentation` which is too disabled by default.
You can enable it with the configuration property `otel.integration.servlet-filter.enabled`.
At last, request processing may reach the specific framework that you application uses.
In this case Spring MVC and `OwnerController.initCreationForm`.

If all instrumentations are enabled, then a new span will be created for every highlighted frame.
All spans from Servlet API will have `kind=SERVER` and name based on corresponding class ana method names,
such as `ApplicationFilterChain.doFilter` or `FrameworkServlet.doGet`.
Span created by Spring MVC instrumentation will have `kind=INTERNAL` and named `OwnerController.initCreationForm`.

The state described above has one significant problem.
Observability backends usually aggregate traces based on their root spans.
This means that ALL traces from any application deployed to Servlet container will be grouped together.
Because their root spans will all have the same named based on common entry point.
In order to alleviate this problem, instrumentations for specific frameworks, such as Spring MVC here,
_update_ name of the span corresponding to the entry point.
Each framework instrumentation can decide what is the best span name based on framework implementation details.
Of course, still adhering to OpenTelemetry
[semantic conventions](https://github.com/open-telemetry/opentelemetry-specification/blob/master/specification/trace/semantic_conventions/http.md).

## Additional instrumentations
`RequestDispatcherInstrumentation` instruments `javax.servlet.RequestDispatcher.forward` and
`javax.servlet.RequestDispatcher.include` methods to create new `INTERNAL` spans around their
invocations.

`ServletContextInstrumentation` instruments `javax.servlet.ServletContext.getRequestDispatcher` and
`javax.servlet.ServletContext.getNamedDispatcher`. The only job of this instrumentation is to
preserve the input parameter of those methods and to make that available for `RequestDispatcherInstrumentation`
described above. The latter uses that name for `dispatcher.target` span attribute.

`HttpServletResponseInstrumentation` instruments `javax.servlet.http.HttpServletResponse.sendError`
and `javax.servlet.http.HttpServletResponse.sendRedirect` methods to create new `INTERNAL` spans
around their invocations.
