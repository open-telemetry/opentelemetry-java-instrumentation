Fixes NATS request overload delegation so nullable responses and synchronous failures cross Byte Buddy advice without executing fallback overloads a second time.

Request futures now finish instrumentation before user callbacks under the captured caller context. Direct cancellation reaches the source future, while derived or already-completed wrappers cannot cancel source work.
