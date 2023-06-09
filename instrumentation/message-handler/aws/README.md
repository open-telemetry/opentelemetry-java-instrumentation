# Using SqsMessageHandler

1. Retrieve a collection of messages to process.
2. Create a SqsMessageHandler and provide the business logic on what to do with the messages.
3. Call the handleMessages function and pass in your messages.
4. It will call the doHandleMessages function you provided wrapped in the messaging span.

```java
Collection<SQSEvent.SQSMessage> sqsMessages;

SqsMessageHandler messageHandler = new SqsMessageHandler(opentelemetry) {
  @Override
  protected void doHandleMessages(Collection<SQSEvent.SQSMessage> messages) {
    // Do my business logic
  }
};

messageHandler.handleMessages(sqsMessages, "destination.name", MessageOperation.RECEIVE);
```
