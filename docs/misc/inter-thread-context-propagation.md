# The story of context propagation across threads

## The need

Take a look at the following two pseudo-code snippets (see below for explanations).

```java
Executor pool = Executors.newFixedThreadPool(10);

public void doGet(HttpServletRequest request, HttpServletResponse response) {
  Future f1 = pool.submit(() -> {
    return userRepository.queryShippingAddress(request);
  });
  Future f2 = pool.submit(() -> {
    return warehouse.currentState(request);
  });
  writeResponse(response, f1.get(), f2.get());
}
```

```java
Executor pool = Executors.newFixedThreadPool(10);

public void doGet(HttpServletRequest request, HttpServletResponse response) {
  final AsyncContext asyncContext = request.startAsync();
  acontext.start(() -> {
    String address = userRepository.queryShippingAddress(request);
    HttpServletResponse response = asyncContext.getResponse();
    writeResponse(response, address);
    asyncContext.complete();
 });
}
```

In both cases, the request processing requires some potentially long operations and the application
developer wants to do them off the main thread. In the first case this hand-off between the request
accepting thread and the request processing thread happens manually by submitting work into some
thread pool. In the second case it is the framework that handles the separate thread pool and
passing work to it.

In cases like this, a proper tracing solution should still combine all the work required for request
processing into a single trace, regardless of what thread that work happened on. With a proper
parent-child relationship between spans, the span representing the shipping address query should be
the child of the span which denotes accepting HTTP request.

## The solution

Java auto instrumentation uses an obvious solution to the requirement above: we attach the current
execution context (represented in the code by `Context`) with each `Runnable`, `Callable` and
`ForkJoinTask`. "Current" means the context that is active on the thread which calls
`Executor.execute` (and its analogues such as `submit`, `invokeAll` etc) at the moment of the call.
Whenever some other thread starts the actual execution of the `Runnable` (or `Callable` or
`ForkJoinTask`), that context get restored on that thread for the duration of the execution. This
can be illustrated by the following pseudo-code:

```java
var job = () -> {
  try(Scope scope = this.context.makeCurrent()) {
    return userRepository.queryShippingAddress(request);
  }
};
job.context = Context.current();
Future f1 = pool.submit();
```

## The drawback

Here is a simplified example of what async servlet processing may look like:

```java
protected void service(HttpServletRequest req, HttpServletResponse resp) {
  // This method is instrumented and we start new scope here
  AsyncContext context = req.startAsync();
  // When the runnable below is being submitted by the servlet engine to an executor service
  // it will capture the current context (together with the current span) with it
  context.start {
    // When Runnable starts, we reactivate the captured context
    // So this method is executed with the same context as the original "service" method
    resp.writer.print("Hello world!");
    context.complete();
  }
}
```

If we now take a look inside the `context.complete` method from above it may be implemented like
this:

```java
// Here we still have the same active context from above.
// It then gets attached to this new runnable
pool.submit(new AcceptRequestRunnable() {
  // The same context from above is propagated here as well
  // Thus new request processing will start while having a context active with some span inside
  // That span will be used as parent spans for new spans created for a new request
  ...
});
```

This means that the mechanism described in the previous section can inadvertently propagate the
execution context of one request to a thread handling an entirely unrelated request. As a result,
the spans representing the acceptance and processing of the second request may be incorrectly linked
to the same trace as those of the first request. This erroneous correlation of unrelated requests
can lead to excessively large traces that remain active for extended periods, potentially lasting
hours.

In addition, this makes some of our tests extremely flaky.

## The currently accepted trade-offs

We recognize the issue of overly aggressive context propagation. However, we believe that providing
out-of-the-box support for asynchronous multi-threaded traces is crucial. To address this, we have
implemented diagnostics to help detect instances where the execution context is propagated too
eagerly. Our goal is to gradually identify and implement framework-specific countermeasures to
address these issues, resolving them one by one.

In the meantime, processing a new incoming request within the given JVM and creating a new `SERVER`
span will always begin with a clean context.
