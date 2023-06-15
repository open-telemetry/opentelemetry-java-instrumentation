# Message Handler

This package contains handlers to instrument message system spans for a batch of messages.

The handler will create a single new messaging span, add span links to it, and set the messaging attributes behind the scene. These values are based off of the messages passed in.

## Available Handlers
- `SqsMessageReceiver` - Receive SQS messages
