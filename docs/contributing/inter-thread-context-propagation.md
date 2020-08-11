# The story of context propagation across threads

## The need
Take a look at the following two pseudo-code snippets (see below for explanations).

```
Executor pool = Executors.newFixedThreadPool(10)

public void doGet(HttpServletRequest request, HttpServletResponse response) {
    Future f1 = pool.submit(() -> {
        return userRepository.queryShippingAddress(requet)
    })
    Future f2 = pool.submit(() -> {
        return warehouse.currentState(requet)
    })
    writeResponse(response, f1.get(), f2.get())
}
```

```
Executor pool = Executors.newFixedThreadPool(10)

public void doGet(HttpServletRequest request, HttpServletResponse response) {
    final AsyncContext acontext = request.startAsync();
    acontext.start(() -> {
            String address = userRepository.queryShippingAddress(requet)
            HttpServletResponse response = acontext.getResponse();
            writeResponse(response, address)
            acontext.complete();
   }
}
```

In both cases request processing requires some potentially long operation and application developer
wants to do them off the main thread. In the first case this hand-off between request accepting thread
and request processing thread happens manually, by submitting work into some thread pool.
In the second case it is the framework that handles separate thread pool and passing work to it.

In cases like this proper tracing solution should still combine into a single trace all the work
required for request processing, regardless in what thread that work happened. With proper
parent-child relationship between span: span representing shipping address query should be the child
of the span which denotes accepting HTTP request.

## The solution
Java auto instrumentation uses an obvious solution to the requirement above: we attach current execution
context (represented in the code by `io.grpc.Context`) with each `Runnable`, `Callable` and `ForkJoinTask`.
"Current" means the context active on the thread which calls `Executor.execute` (and its analogues
such as `submit`, `invokeAll` etc) at the moment of that call. Whenever some other thread starts
actual execution of that `Runnable` (or `Callable` or `ForkJoinTask`), that context get restored
on that thread for the duration of the execution. This can be illustrated by the following pseudo-code:

```
    var job = () -> {
        try(Scope scope = withScopedContext(this.context)) {
            return userRepository.queryShippingAddress(requet)
        }}
    job.context = Context.current()
    Future f1 = pool.submit()

```

## The drawback
There are some runtime environments which, simplifying, do the following:
```
pool.submit(new AcceptRequestRunnable() {
    Request req = readRequest()
    service(req){ <- This method is instrumented and we start new scope here
        // At this point Context.current() will have a started span
        // recording monitoring data about `req`
        pool.submit(new ProcessRequestRunnable(req) {
            // The same context from above is active here
            writeResponse(process(req))
            pool.submit(new AcceptRequestRunnable() {
            // The same context from above is propagated here as well
            ... repeat until shutdown
        })
    }
})
```

This means that mechanism described in the previous section will propagate the execution context
of one request processing to a thread accepting some next, unrelated, request.
This will result in spans representing the accepting and processing of the second request will join
the same trace as those of the first span. This mistakenly correlates unrelated requests and may lead
to huge traces being active for hours and hours.

In addition this makes some of our tests extremely flaky.

## The currently accepted trade-offs
We acknowledge the problem with too active context propagation. We still think that out of the box
support for asynchronous multi-threaded traces is very important. We have diagnostics in place to
help us with detecting when we too eagerly propagate the execution context too far. We hope to
gradually find framework-specific countermeasures to such problem and solve them one by one.

In the meantime, processing new incoming request in the given JVM and creating new `SERVER` span
always starts with a clean context.
