# Message Handler

This package contains handlers to instrument message system spans for a batch of messages. It is not designed for a for-loop case.

The handler will create a single new messaging span, add span links to it, and set the messaging attributes behind the scene. These values are based off of the messages passed in.

The handler provides constructors to change the messaging operation and span name of the newly created messaging span.

## Available Handlers
- `SqsMessageHandler` - processes messages from Amazon's SQS.
