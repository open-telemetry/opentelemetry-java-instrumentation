# OpenTracing API


The OpenTracing group offers an API to instrument your code. 
This document is a kind of a "quick start" for the official specification: https://github.com/opentracing/specification

There are several concepts exposed by the OpenTracing API:

* The core API used for instrumenting the code
* The tracer implementations are in charge of capturing and reporting the traces. For instance `dd-trace` generates and reports traces to the Datadog trace agent.
* In-process trace propagation for trace consistency in a concurrent/asynchronous request context.
* Distributed trace propagation for when receiving a request and making external calls.

 
## OpenTracing Core API

Official documentation link: [Opentracting Tracer specification](https://github.com/opentracing/specification/blob/master/specification.md)

The core API exposes 3 main objects:

* A [Tracer](https://github.com/opentracing/specification/blob/master/specification.md#tracer)
* A [Span](https://github.com/opentracing/specification/blob/master/specification.md#span) 
* A collection of **Tags** associated with a Span


### Tracers

The tracer is in charge of instantiating new spans for a given context, and sending them to the appropriate sink when complete.

The tracer instantiation depends of the implementation you chose. For instance, `dd-trace` allows you
to send the traces to a logger or directly to a running Datadog agent.

```java
  // Initialize the Datadog Java Tracer to write traces to the log:
  Tracer tracer = new DDTracer();
```

After a tracer is created, you will usually want to register it with the `GlobalTracer`
to make it accessible all OpenTracing instrumentation in your JVM.

```java
  io.opentracing.util.GlobalTracer.register(tracer);
```

### Spans
Once a tracer is instantiated, you can use it to create and manage span. OpenTracing defines a SpanBuilder
accessible through the method `buildSpan(String operationName)` to serve this purpose.

```java
  // Create a new Span with the operation name "componentTracking"
  ActiveSpan current = tracer.buildSpan("componentTracking").startActive(true);
```

This example creates a simple span referenced "componentTracking". The `startActive()` method starts a new span and sets it
as the active span. This means that any new span created going forward on the same thread will reference this as its' parent.
If another span is already active, the new span will replace it. 

**A collection of related spans is called a trace.**

Sometimes you need to create a span without promoting it as the active. If you want to do that, use the `startManual()`
 method instead.
   
```java
  // Create a span, but do not promoting it as the active span 
  Span anotherSpan = tracer.buildSpan("componentTracking").start();
```

Typically, span creations are made in the beginning of the methods you want to trace. 
And of course, you need to finish/close the span in order to get the operation duration.
This is achieved using the `finish` method.

```java
  // Finishing the tracing operation
  current.finish()
```

**Be careful!!** All children spans must be finished/closed before finishing the root span.
If child spans are unfinished when the parent attempts to finish, the span will remain incomplete and risk being unreported.


Now, you are able to create, start and stop very simple spans. 
You can manipulate them at any time and add extra contextual information using the tags.

### Tags

Tags are local to each span and no tags will be inherited from the parent. Information relevant to all spans in a trace
should be stored as [baggage](#baggage).

OpenTracing defines a [standard set of tags](https://github.com/opentracing/specification/blob/master/semantic_conventions.md#standard-span-tags-and-log-fields) and should be used appropriately.  Custom tags can also be defined as needed. 

```java
  // Create a span and set it as the active span
  ActiveSpan valuableSpan = tracer.
      buildSpan("componentTracking")
      .withTag("custom-meta", "some-useful-value")
      .withTag(Tags.COMPONENT, "my-component-mysql")
      .startActive(true);


  // Somewhere further in the code
  Tags.HTTP_URL.setTag(valuableSpan, "https://my-endpoint/resource/item");
  Tags.HTTP_STATUS.setTag(valuableSpan, 200);
```

### Baggage

Information relevant for the entire trace is stored as baggage. 
Baggage is very similar to tags, but has important distinctions.  Baggage is:
* Associated with all spans for a trace.
* Propagated outside the trace context via HTTP or Messaging protocols (depends of the tracer implementation). 

```java
  // Like tags, you can add baggage item to the span
  valuableSpan.setBaggageItem("username", "modernmajorgeneral");
```

### Errors

Errors are manually captured in a span by setting the error flag and logging the error attributes.

```java
  try {
    // your code
  } catch (Exception e) {
    // capture error
    ActiveSpan span = GlobalTracer.get().activeSpan();
    Tags.ERROR.set(span, Boolean.TRUE);
    span.log(Collections.singletonMap(io.opentracing.log.Fields.ERROR_OBJECT, e));

    // recovery code
  }
```

## OpenTracing Asynchronous API

An `ActiveSpan` can generate a `Continuation` as a way of propagating traces from across a thread boundary.

On the parent thread:
```java
  Continuation spanContinuation = valuableSpan.capture();
  // pass the continuation onto a new thread
```

On a different thread:
```java
  ActiveSpan valuableSpan = spanContinuation.activate();
  // span is now active on the new thread
```

## OpenTracing Cross Process Propagation

Spans are associated across processes in a trace via the `Tracer.extract` and `Tracer.inject` methods.

```java
  // On the start of a new trace in an application, associate incoming request with existing traces.
  SpanContext spanCtx = tracer.extract(Format.Builtin.HTTP_HEADERS, someTextMapInstance);
  ActiveSpan currentSpan = tracer.buildSpan("componentTracking").asChildOf(spanCtx).startActive(true);
```

```java
  // When making an external call propagate the trace by injecting it into the carrier...
  tracer.inject(currentSpan.context(), Format.Builtin.HTTP_HEADERS, someTextMapInstance);
```
