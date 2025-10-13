# AWS Lambda Instrumentation

We provide two packages for instrumenting AWS lambda functions.

- [aws-lambda-core-1.0](./aws-lambda-core-1.0/library) provides lightweight instrumentation of the Lambda core library, supporting
  all versions. Use this package if you only use `RequestStreamHandler` or know you don't use any event classes from
  `aws-lambda-java-events`. This also includes when you are using `aws-serverless-java-container` to run e.g., a
  Spring Boot application on Lambda.

- [aws-lambda-events-2.2](./aws-lambda-events-2.2/library) (Library instrumentation is deprecated) provides instrumentation of the Lambda library,
  including standard and custom event types, from `aws-lambda-java-events` 2.2+.

- [aws-lambda-events-3.11](./aws-lambda-events-2.2/library) provides instrumentation of the Lambda library, including standard and custom event
  types, from `aws-lambda-java-events` 3.11+.
