# OpenTracing API


The Opentraction group offers an API to instrument your code. 
This document is a kind of a "Quick start for the" official specifications: https://github.com/opentracing/specification

There are several concepts expose by the OpenTracing API:

* The core API used for instrumenting the code
* The tracer implementations, in charge of generating physically the traces. For instance the Datadog Java Tracer generates
traces that can be consumed by the Datadog agent.
* The Asynchronous API to help developers managing their spans and traces in a concurrency context.

 
## OpenTracing Core API

Official documentation link: [Opentracting Tracer specification](https://github.com/opentracing/specification/blob/master/specification.md#tracer)

The core API exposes 3 main objects:

* A **Tracer**
* A **Span** 
* A collection of **Tags**



The tracer is in charge of instantiate new spans, and sending them to the appropriate sink.

The tracer instantiate depends of the implementation you chose. For instance, the Datadog Java Tracer allows you
to send the traces to a logger or directly to a running Datadog agent.

```java
    // Initialize the Datadog Java Tracer
    Tracer tracer = new DDTracer();
```

Once a tracer is instantiated, you can use it to create and manage span. OpenTracing defines a SpanBuilder accessible through
the method `buildSpan(String operationName)` to serve this purpose.


```java
    // Create a new Span with the operation name "componentTracking"
    Span current = tracer.buildSpan("componentTracking").startActive();
```

This example creates a simple span referenced "componentTracking". The `startActive()` method starts a new span and set it
as the active. This means that all new spans created after will be related to this one as children. If a span is already 
the active one, the new span will replace it. 

**A collection of related spans is called a trace.**

But, sometimes you need to create a span without promoting it as the active. If you want to do that, use the `startManual()`
 method instead.
   
   
```java
    // Create a span, but do not promoting it as the active span 
    Span anotherSpan = tracer.buildSpan("componentTracking").startManual();
```


Typically, span creations are made in the begging of the methods you want to trace. 
And of course, you need to finish/close the span in order to get the operation duration.
This is achieving using the `finish` method.

```java
    // Finishing the tracing operation
    current.finish()
```

**Be careful!!** You have to be sure that all children spans are finished/closed before calling the method on the root span.
If you don't do this, you may face to span incomplete issues or some traces/spans will not be reported by the tracer.


Now, you are able to create, start and stop very simple spans. 
You can manipulate them at any time and add extra information using the tags.
Tags are local to the span. So, no tags will be inherit from the parent. In order to propagate meta accross all spans of a
trace, use the `baggageItems` (see right after).

OpenTracing Tags are standardized meta and allow developers to add more value to the span. 

```java
    // Create a span, but do not promoting it as the active span 
    Span valuableSpan = tracer.
        buildSpan("componentTracking")
        .withTag("custom-meta", "some-useful-value")
        .withTag(Tags.COMPONENT, "my-component-mysql")
        .startActive();


    // Somewhere further in the code
    Tags.HTTP_URL.setTag(valuableSpan, "https://my-endpoint/resource/item");
    Tags.HTTP_STATUS.setTag(valuableSpan, 200);
```

All standardized tags can be consulted there: [OpenTracing Semantic specification](https://github.com/opentracing/specification/blob/master/semantic_conventions.md)

So, tags are local to the span. If you want set for meta for a trace, you have to use `baggabeItems` instead.
Baggage are very similar to the tags, but they have a powerful capabilities:
* A baggage is attached to all spans of a trace.
* A baggage is propagated outside the trace context via Http or Tcp protocols (depends of the tracer implementation). 

```java
    // Like tags, you can add baggage item to the span
    valuableSpan.setBaggageItem("username", "@gpolaert");
```


## OpenTracing Asynchronous API
see WIP: https://github.com/opentracing/specification/issues/23
