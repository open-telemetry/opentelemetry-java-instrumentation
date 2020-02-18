package io.opentelemetry.smoketest

class SpanCounter {

  long expiration;

  final Map<String, Integer> targets

  boolean timedOut = false

  int totalTargets = 0

  boolean finished

  final Map<String, Integer> counters

  SpanCounter(Map<String, Integer> targets, long timeout) {
    expiration = System.currentTimeMillis() + timeout
    this.targets = targets
    counters = new HashMap<>(targets.size())
    targets.keySet().forEach({
      totalTargets += targets[it]
      counters[it] = 0
    })
  }

  void run(File file) {
    def runnable = {
      def reader

      try {
        reader = file.newReader()
        reader.skip(file.length())

        def line

        while (System.currentTimeMillis() < expiration) {
          line = reader.readLine()
          if (line) {
            counters.keySet().forEach({
              if (line.startsWith(it)) {
                counters[it]++
                if (--totalTargets == 0) {
                  // We hit our total target. We may or may not have gotten the right
                  // number for each tag, but we're letting the caller sort that out!
                  synchronized (this) {
                    finished = true
                    this.notify()
                  }
                }
              }
            })
          } else {
            Thread.currentThread().sleep(10)
          }
        }
        // We ran out of time. Let the waiter know!
        timedOut = true
        synchronized (this) {
          finished = true
          this.notify()
        }

      }
      finally {
        task.cancel()
        reader?.close()
      }
    } as Runnable

    def t = new Thread(runnable)
    t.start()
  }

  boolean isTimedOut() {
    return timedOut
  }

  Map<String, Integer> waitForResult() {
    synchronized (this) {
      while (!finished) {
        this.wait()
      }
    }
    return counters
  }
}
