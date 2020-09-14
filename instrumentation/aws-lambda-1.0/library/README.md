# AWS Lambda Instrumentation

This package contains libraries to help instrument AWS lambda functions in your code.

To use the instrumentation, replace your function classes that implement `RequestHandler` with those
that extend `TracingRequestHandler`. You will need to change the method name to `doHandleRequest`.

```java
public class MyRequestHandler extends TracingRequestHandler<String, String> {
  // Note the method is named doHandleRequest instead of handleRequest.
  @Override
  protected String doHandleRequest(String input, Context context) {
    if (input.equals("hello")) {
      return "world";
    }
    return "goodbye";
  }
}
```

A `SERVER` span will be created with the name you specify for the function when deploying it.
