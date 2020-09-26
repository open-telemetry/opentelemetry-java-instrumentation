### Debugging

Debugging java agent can be a challenging task since some instrumentation
code is directly inlined into target classes and debugger is
usually not attached early enough to activate breakpoints
in agent initialization code (`premain`/`agentmain`, `agent-tooling` artifact etc.).

#### Template methods and agent initialization

Breakpoints do not work in template methods, because their code is directly inlined
by ByteBuddy into the target class. It is good to keep these methods as small as possible.
The template methods are annotated with:

```java
@net.bytebuddy.asm.Advice.OnMethodEnter
@net.bytebuddy.asm.Advice.OnMethodExit
```

The best approach to debug template methods and agent initialization is to use the following statements:

```java
System.out.println()
Thread.dumpStack()
```

#### Enable debugger

The following example shows remote debugger configuration. The breakpoints
should work in any code except ByteBuddy templates and agent initialization code.

```bash
java -javaagent:opentelemetry-javaagent-<version>-all.jar -jar -agentlib:jdwp="transport=dt_socket,server=y,suspend=y,address=5000" app.jar
```
