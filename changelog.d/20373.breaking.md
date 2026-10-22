Emit only stable source code attributes: `code.function.name` replaces `code.namespace` and
`code.function`, and log records use `code.file.path` and `code.line.number` instead of
`code.filepath` and `code.lineno`. The `code` and `code/dup` opt-ins and
`general.code.semconv` declarative settings no longer select legacy emission.
