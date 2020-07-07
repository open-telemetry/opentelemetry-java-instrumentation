# Instrumentation Core

These modules for the core logic for library instrumentation. [instrumentation](../instrumentation)
should add core logic here which can be set up manually by a user, and agent-specific code
for automatically setting up the instrumentation in that folder.

Note, we are currently working on separating instrumentatin projects so that their core parts can
be accessed by users not using the agent. Due to the current Gradle setup, we have these two top-level
folders, instrumentation and instrumentation-core, but eventually we want to move to flattening them
into something like

```
instrumentation/
  aws-sdk-2.2/
    aws-sdk-2.2/
    aws-sdk-2.2-auto/
```

## Shading core instrumentation

The instrumentation in this folder is intended for use both directly from user apps and from the
agent when it automatically adds instrumentation to a user app. This means that the same library may
be used both by the agent and the app at the same time, so to prevent any conflicts, we make sure to
use a shaded version from the agent, which is not published for use from users, e.g.,

```
shadowJar {
  archiveClassifier = 'agent'

  configurations = []

  relocate 'io.opentelemetry.instrumentation.awssdk.v2_2', 'io.opentelemetry.auto.instrumentation.awssdk.v2_2.shaded'
}
```