# Message Handler

This package contains handlers to instrument message system spans for a batch of messages. It is not designed for a for-loop case.

The handler will create a single new messaging span, add span links to it, and set the messaging attributes behind the scene. These values are based off of the messages passed in.

The handler provides constructors to change the messaging operation and span name of the newly created messaging span.

## Available Handlers
- `SQSBatchMessageHandlerTest` - processes messages from Amazon's SQS.

## Using SQSBatchMessageHandlerTest

1. Retrieve a collection of messages to process.
2. Create a SQSBatchMessageHandler and provide the business logic on what to do with the messages.
3. Call the handleMessages function and pass in your messages.
4. It will call the doHandleMessages function you provided wrapped in the messaging span.

```java
Collection<SQSEvent.SQSMessage> sqsMessages;

SqsBatchMessageHandler messageHandler = new SqsBatchMessageHandler(GlobalOpenTelemetry.get()) {
  @Override
  protected void doHandleMessages(Collection<SQSEvent.SQSMessage> messages) {
    // Do my business logic
  }
};

messageHandler.handleMessages(sqsMessages);
```
