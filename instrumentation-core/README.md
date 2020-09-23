# Instrumentation Core

These modules for the core logic for library instrumentation. [instrumentation](../instrumentation)
should add core logic here which can be set up manually by a user, and agent-specific code
for automatically setting up the instrumentation in that folder.

Note, we are currently working on separating instrumentation projects so that their core parts can
be accessed by users not using the agent. Due to the current Gradle setup, we have these two top-level
folders, instrumentation and instrumentation-core, but eventually we want to move to flattening them
into something like

```
instrumentation/
  aws-sdk/
    aws-sdk-2.2/
      auto/
```
