# HashMap Summary

## Structure

An array of slots (buckets). Each bucket holds a linked list, or a tree if it gets long.
Index = hash & (n - 1), where n is the table size.

## Why the table size is a power of two

It makes n - 1 all ones in binary, so every slot can be chosen. Otherwise some slots could never be used.
On resize (doubling), each entry either stays in its slot or moves by exactly the old size.
new HashMap<>(10) rounds up to 16. The number you pass is slots, not entries.

Spread (h ^ (h >>> 16))

Only the low bits of the hash pick the slot, so hashes that differ only in the high bits would all collide.
The spread mixes high bits into low bits, which is cheap insurance.

## Resizing

Triggered when total entries exceed capacity * 0.75. It counts all entries, not occupied slots.
It copies every entry, so the one put that triggers it is slow.
To store 1,000 entries without resizing, you need capacity above 1000 / 0.75, which becomes 2048.
Too big a capacity wastes memory and makes iteration slow.

## Long buckets (a separate trigger)

When one bucket reaches 8 entries:
Table under 64 slots: resize, because it’s probably just crowded.
Table 64 or more: convert that bucket to a tree, because bad hashes are the likely cause.
A tree makes lookup in that bucket O(log n) instead of O(n). It reduces the damage but doesn’t fix bad hashes.
It goes back to a list at 6 or fewer entries.
Some buckets can be lists while others are trees.
Neither resizing nor treeifying is a full fix. A good hashCode is the real fix.

## Mutable keys

Each stored entry keeps its old hash. If you change a key after inserting, get and remove both miss.
The entry is still in the map and shows up in iteration, but nothing can remove it normally. That is a memory leak.
Keys should be immutable.

## Thread safety

Plain HashMap is not safe. Problems: lost updates, wrong size, entries lost during resize, stale reads.
Java 7 could loop forever during a concurrent resize. Java 8 fixed the hang, but data can still be lost.

Hashtable locks the whole map, so threads wait even for different keys.
You worked out the fix: lock per bucket. That leads to ConcurrentHashMap.

# ConcurrentHashMap Summary

## Why it exists

HashMap: no locks, not thread-safe, fast.
Hashtable and Collections.synchronizedMap: one lock for the whole map, so threads wait even for different keys.
ConcurrentHashMap: thread-safe and much more parallel.

## How writes work (Java 8+)

Empty bucket: the first node is placed using CAS (compare-and-set, one atomic CPU instruction). The loser of a race retries and sees a non-empty bucket.
Non-empty bucket: synchronized on the head node of that bucket only. Threads wait only if they hit the same bucket.
All writers (put, remove, compute, merge, resize) use the same lock, so no one can modify a bucket behind your back.
Java 7 used fixed groups of buckets (segments), one lock per segment.

## How reads work

get takes no lock. Table slots and node val/next fields are volatile.
A writer builds the node fully, then links it in. A reader sees either the old or the new state, never a half-built node.
A get may miss a put that is still in progress. That is correct behavior.

## Volatile

It does not mean “bypass CPU cache”. It prevents reordering and creates happens-before: if you see the write, you see everything before it.

## Single calls are safe, sequences are not

containsKey then put: two threads can both see “absent”, and the second overwrites the first.
get then put(+1): lost update.
Use atomic methods: putIfAbsent, computeIfAbsent, merge, compute.
putIfAbsent: you build the value first (eager). computeIfAbsent: the function runs only if absent (lazy), under the bucket lock.
Keep the function short, and never touch the same map inside it.

## Counters

AtomicLong: CAS on one location. Exact value, but many CAS retries under heavy contention.
LongAdder: base value plus cells, threads spread across cells under contention. Fast for metrics, but sum() is not exact during updates.
Choice depends on use: exact decision (limits, stock) means AtomicLong; statistics means LongAdder.

## No nulls

get returning null must mean “absent”. With nulls allowed, you can’t tell absent from “present with null”, because containsKey then get isn’t safe when other threads change the map between calls.
HashMap can allow nulls because single-thread use makes the check reliable.

## Iteration and size

Iterators are weakly consistent: never throw CME, may or may not show changes made after they started. Removing during iteration is safe.
size() uses a base counter plus cells (like LongAdder), so it is approximate under updates. Prefer mappingCount() for huge maps.
Don’t use size() for hard limits. Use a Semaphore or an atomic counter.

## ConcurrentHashMap vs synchronizedMap

synchronizedMap: one lock, iteration needs manual locking, compound operations unsafe.
ConcurrentHashMap: lock per bucket, lock-free reads, safe iteration, atomic compound methods. Better for read-heavy, many-thread use.

## Java Memory Model (volatile, happens-before)
## Thread pools and ExecutorService
## synchronized vs ReentrantLock

# Multithreading

## Why multithreading

CPU-bound work: split across cores, so things truly run in parallel.
I/O-bound work: while one thread waits, another uses the CPU.
Thread count: CPU-bound is about the number of cores. I/O-bound is cores × (1 + wait/compute), capped by what you wait on (e.g. DB connection pool size).
Costs: complexity, memory per thread (about 1 MB stack), context switching.

## Pitfalls

Race condition: the result depends on thread timing. It has two separate causes:
- Atomicity: a multi-step operation gets interleaved (count++). Fix: synchronized or atomics.
- Visibility: one thread doesn’t see another’s write (a stop flag). Fix: volatile.

Deadlock: threads wait for each other forever. The simplest fix is a consistent lock order (e.g. by account ID). 
tryLock(timeout) also helps by setting a timeframe for acquiring lock, skipping it once timeframe is over.

Hot locks: many threads queue on one lock. Fixes: shorter critical sections, splitting the data, a queue per key.

Starvation: a thread rarely or never gets what it needs, like due to unfair lock assign,ment or unfair OS scheduling.
Livelock: threads stay active but make no progress.

## Thread states

NEW: object created, start() not called.
RUNNABLE: stsrt() called, waiting in the OS queue for a core, or running on a core.
BLOCKED: in lock queue, waiting for a signal that it can compete for a lock for a synchronized block.
WAITING / TIMED_WAITING: in lock queue, waiting for a signal that it can compete for a reentrant lock.
TERMINATED: finished.
BLOCKED, WAITING and TIMED_WAITING use no CPU. They only hold memory.
Many RUNNABLE threads with high CPU means more work than cores. The queued ones use no CPU.

## How a lock works

A lock holds a state: free, or owned by thread X.
Acquire: the thread does an atomic CAS on that state. Release: it resets the state to free.
A thread that fails to acquire joins the lock’s queue and waits.
On release, one waiter is signalled. The signal means “you may try now”, not “you own it”.
The woken thread must first get a core from the OS scheduler, then tries the lock.

## synchronized vs ReentrantLock

Both protect a critical section and are reentrant (the same thread can re-acquire; ReentrantLock keeps a count).
synchronized: automatic release, no timeout, not interruptible while waiting, no fairness guarantee.
ReentrantLock: manual unlock() in finally, plus tryLock(timeout), lockInterruptibly() and an optional fair mode.

normal reentrant lock
```
lock.lock(); // wait forever, no timeframe, no interruption
try {
    // critical section
} finally {
    lock.unlock();
}
```
tryLock (no wait):
```
if (lock.tryLock()) {
    try {
        // critical section
    } finally {
        lock.unlock();
    }
} else {
    // lock busy, do something else
}
```
tryLock (with timeout):
```
if (lock.tryLock(2, TimeUnit.SECONDS)) {   // throws InterruptedException
    try {
        // critical section
    } finally {
        lock.unlock();
    }
} else {
    // timed out
}
```
Interruptible:
```
lock.lockInterruptibly();   // throws InterruptedException
try {
    // critical section
} finally {
    lock.unlock();
}
```

Timeout is decided in advance (“wait at most 2 s”). Interruptible is decided later by someone else (e.g. shutdown).
Rule of thumb: use synchronized by default, and ReentrantLock only when you need those extras.

## Fair vs unfair reentrant lock

Unfair (default): a new arrival can take the free lock before the woken waiter gets a core. The lock never sits idle, 
so throughput is higher, but a waiter can lose repeatedly.
Fair: new arrivals queue behind. No starvation, but the lock sits idle during each wake-up gap, so throughput is lower.
Don’t make locks fair by default. Use fair only when starvation is a real, observed problem.

## Synchronized, Volatile, Atomics

### Synchronized gives both atomicity and visibility. Visibility works only between threads using the same lock object.
Volatile gives visibility only. No lock, no waiting, so it is cheap.
Volatile is safe only when each operation is a single read or a single write (a stop flag). It is not safe for count++ or if (x > max) max = x, because both are read-then-write.

### Locking rules

Every shared variable must be guarded by one specific lock, and every access (read and write) must use it.
Different lock objects mean no protection. Common mistakes: synchronized (this) with a shared static field, 
locking on a field that gets reassigned, locking on a String literal or boxed Integer.
A non-synchronized read of a variable written under a lock is a visibility bug, not an atomicity bug: the reader may see a stale value.
Fixes: synchronize the getter too, or make the variable volatile (fine when the only unsynchronized access is a single read).
Volatile alone fails if the writer does balance += amount (read, add, write), so lost updates are possible.

### Hardware view

Each core has its own cache. The cache coherence protocol (MESI) keeps copies in agreement: before a write, other cores’ copies are invalidated.
volatile is not “skip the cache”. It stops the JIT from keeping the value in a register, and it inserts memory barriers to stop reordering.
There is no shared clock between cores. The guarantee is on the agreed order of operations on that variable: a read ordered after the write sees it. If two operations are too close to tell, either outcome is legal, and the program must be correct with both.

### Atomics and CAS

CompareAndSet(expected, new): set only if the value is still expected, as one atomic CPU instruction (LOCK CMPXCHG on x86).
addAndGet, incrementAndGet, updateAndGet, accumulateAndGet already contain the retry loop. You write your own loop only for logic they can’t express, and the calculation inside must have no side effects, since it re-runs on failure.
A failed CAS means the thread retries: it stays RUNNABLE and uses CPU (spinning). No lock, no queue, no deadlock, and a paused thread blocks nobody.
Under heavy contention many retries waste CPU. That is the AtomicLong vs LongAdder point.

### When to use which

Atomics: one variable, small operation, low to moderate contention.
Locks: several variables that must change together, long operations or I/O, heavy contention.
Two AtomicLongs are each atomic, but a transfer using both is not atomic as a whole. Another thread can see the in-between state.
Use a lock covering both.

## Wait and Notify

### The problem

A consumer finds the queue empty and must wait for a producer to add an item.

Busy waiting burns a full core.
```
while (empty) {}
```

Polling with Thread.sleep uses no CPU but may react late and may wake up for nothing.
wait() / notify() lets the thread wait with no CPU and no polling, and be signalled exactly when something changes.

### Thread.sleep vs wait()

sleep(ms): waits for time to pass, state TIMED_WAITING. No lock queue involved. It keeps any locks it holds. It can be interrupted.
wait(): waits for a signal from another thread, state WAITING. It releases the lock.
TIMED_WAITING only means “waiting with an alarm”. The stack trace in a thread dump shows what it is really waiting for.

### API (methods of every object)

wait(): release the lock, wait for a signal.

notify(): wake one thread from that object’s wait set.

notifyAll(): wake all threads in the wait set.

These methods must be called only while holding that object’s synchronized lock, otherwise IllegalMonitorStateException.

### What wait() does

Releases the lock on the object (otherwise the producer could never enter).
Puts the thread in the object’s wait set (WAITING).
Blocks inside wait() for as long as it takes. When notified, the thread must re-acquire the lock before wait() returns, because the code after it is still inside the synchronized block.

Two queues per object

Entry queue: threads that want to enter synchronized but can’t. State BLOCKED.
Wait set: threads that entered and then called wait(). State WAITING.
notify() looks only at the wait set. It moves one thread from the wait set to the back of the entry queue.
Being notified means “you are now in line for the lock”, not “you go next”.
On each lock release, one thread is woken from the entry queue (which one is unspecified for synchronized).
If nobody is in the wait set when notify() is called, the signal is lost. It is not remembered.

### Why while, not if

Consumer:
```
synchronized (queue) {
    while (queue.isEmpty()) {   // while, not if
        queue.wait();           // releases lock, waits, re-acquires on wake
    }
    item = queue.remove();
}
```
Producer:
```
synchronized (queue) {
    queue.add(item);
    queue.notify();             // wake one waiting thread
}
```

Another consumer may take the item between the notify and this thread getting the lock back. The queue is empty again.
Being woken means “go and look”, not “your condition is now true”.
while re-checks the condition after every wake-up. if would call remove() on an empty queue.
It also protects against spurious wakeups (a wake-up with no notify at all).

### Prefer notifyAll over notify

The wait set can contain different kinds of threads, for example consumers waiting for “queue not empty” and producers waiting for “queue not full”. 
notify() wakes just one, and it might be the wrong kind. That thread re-checks its condition, finds it false, and waits again, while the signal is used up and the right thread keeps sleeping. 
Everyone may end up waiting forever. notifyAll() wakes all of them, each re-checks its own condition in the while loop, and the right ones proceed.
Prefer notifyAll() unless you are sure that all waiting threads are interchangeable and any one of them can use the signal.The cost is some wasted wake-ups.

## Thread Pools and ExecutorService

### Why pools exist

Thread per request causes:
- Memory issues as creating a thread needs memory(~ 1 MB per thread)
- Too many threads means lots of context switching for CPU instead of doing real work
- Creating a thread itself takes time, and if real task is tiny then creating a thread for task takes more time than task itself, making thread creation major factor in overall latency
- No upper limit on number of threads when a spike happens

A pool creates a limited set of threads once and reuses them. Tasks wait in a queue until a worker is free.

### How a pool works

- Each worker runs a loop: take a task from the queue, run it, come back for the next.
- The queue is a BlockingQueue. Its take() makes a worker wait (no CPU) when the queue is empty, and wakes one when a task arrives.
- BlockingQueue is built on ReentrantLock and Condition, the same idea as wait/notify.
- Workers are created lazily, as tasks are submitted.
- The producer is whoever calls submit(): your code, or a framework. Tomcat’s request pool works this way, with each HTTP request submitted as a task.

### The types, and who is what

- ExecutorService: the interface (submit(), shutdown(), ...).
- ThreadPoolExecutor: the main JDK class implementing it. It holds the workers and the queue.
- Executors.newFixedThreadPool(n) and similar static factory methods, not classes. They only build a ThreadPoolExecutor with preset settings.
- ScheduledThreadPoolExecutor is a subclass, used for delayed or repeated tasks.
- Not ThreadPoolExecutor: ForkJoinPool (parallel streams, CompletableFuture default) and the virtual-thread executor (newVirtualThreadPerTaskExecutor, an internal JDK class with no queue or core size).
- BlockingQueue can also be used directly for your own producer-consumer design.

### Settings of ThreadPoolExecutor

- Core size, maximum size, queue, keep-alive time, rejection policy.
- Order of decisions on submit():
  - Fewer workers than core size: start a new worker.
  - Otherwise, put the task in the queue.
  - Queue full: start extra workers, up to maximum size.
  - Maximum reached and queue full: reject.
- The extra workers (above core) are removed after the keep-alive time if they sit idle.

### Rejection policies

- AbortPolicy (default): throws RejectedExecutionException.
- CallerRunsPolicy: the submitting thread runs the task itself, inside submit(). Nothing is lost, and the caller slows down, which acts as back-pressure.
- DiscardPolicy: silently drops the task.
- DiscardOldestPolicy: drops the oldest queued task and retries.

### The Executors shortcuts and their risks

- newFixedThreadPool(n): unbounded queue, so memory can run out.
- newCachedThreadPool(): unbounded thread count, so it can recreate the original problem.
- newSingleThreadExecutor(): one worker, tasks run in order.
- newScheduledThreadPool(n): delayed or repeated tasks.

Production services usually build a ThreadPoolExecutor with the constructor (bounded queue, chosen rejection policy). No subclassing is needed, only different arguments.
The shortcuts are fine for scripts, tests and limited, known loads.