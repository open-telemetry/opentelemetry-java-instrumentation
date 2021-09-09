# Instrumentation for Java Servlets

## A word about version

We support Servlet API starting from version 2.2. 
But various instrumentations apply to different versions of the API.

They are divided into the following sub-modules:
- `servlet-common` contains shared code for both `javax.servlet` and `jakarta.servlet` packages
  - `library` contains the abstract tracer applicable to all servlet versions given an
    implementation of `ServletAccessor` to access request and response objects of the specific
    version
  - `javaagent` contains shared type instrumentations which can be used by version specific modules
    by specifying the base package and advice class to use with them. Contains some helper classes
    used by advices to reduce code duplication. It does not define any instrumentation modules and
    is used only as a dependency for other `javaagent` modules.
- Version-specific modules where `library` contains the version-specific tracer and request/response
  accessor, and `javaagent` contains the instrumentation modules and advices.
  - `servlet-javax-common` contains instrumentations/abstract tracer common for Servlet API versions `[2.2, 5)`
  - `servlet-2.2` contains instrumentations/tracer for Servlet API versions `[2.2, 3)` 
  - `servlet-3.0` contains instrumentations/tracer for Servlet API versions `[3.0, 5)`
  - `servlet-5.0` contains instrumentations/tracer for Servlet API versions `[5,)`

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
<b>at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:119)</b>
...
at org.apache.tomcat.util.net.SocketProcessorBase.run(SocketProcessorBase.java:49)
at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1128)
at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:628)
at org.apache.tomcat.util.threads.TaskThread$WrappingRunnable.run(TaskThread.java:61)
at java.base/java.lang.Thread.run(Thread.java:834)
</pre>

Everything starts when HTTP request processing reaches the first class from Servlet specification.
In the example above this is the
`OncePerRequestFilter.doFilter(ServletRequest, ServletResponse, FilterChain)` method.
Let us call this first servlet specific method an "entry point".
This is the main target for `Servlet3Instrumentation` and `Servlet2Instrumentation`:

`public void javax.servlet.Filter#doFilter(ServletRequest, ServletResponse, FilterChain)`

`public void javax.servlet.http.HttpServlet#service(ServletRequest, ServletResponse)`.

These instrumentations are located in separate submodules `servlet-3.0`, `servlet-2.2` and `servlet-5.0`,
because they and corresponding tests depend on different versions of the servlet specification.

At last, request processing may reach the specific framework that your application uses.
In this case Spring MVC and `OwnerController.initCreationForm`.

If all instrumentations are enabled, then a new span will be created for every highlighted frame.
All spans from Servlet API will have `kind=SERVER` and name based on corresponding class and method names,
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
`HttpServletResponseInstrumentation` instruments `javax.servlet.http.HttpServletResponse.sendError`
and `javax.servlet.http.HttpServletResponse.sendRedirect` methods to create new `INTERNAL` spans
around their invocations.
