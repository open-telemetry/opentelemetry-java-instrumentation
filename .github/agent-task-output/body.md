Move Flow instrumentation into the Kotlin coroutines 1.0 javaagent project, with separate Muzzle passes for its compatibility ranges. Existing enablement names and runtime behavior stay unchanged.

Keep the Kotlin Flow helper project separate because Muzzle generation cannot process its Kotlin sources.

Part of #20189.
