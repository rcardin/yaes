## yaes-data

Contains data structures for use with effects. Depends on `yaes-core`.

### Channels

Based on `java.util.concurrent` blocking queues with suspending operations.

**Types:** Unbounded, Bounded (with overflow strategies), Rendezvous

**Overflow strategies for bounded channels:** SUSPEND (default), DROP_OLDEST, DROP_LATEST

**Closing vs. Canceling:** `close()` allows draining remaining elements, `cancel()` clears immediately.

**Producer DSL:** `produce` and `produceWith` for convenient channel creation.

**Channel-Flow bridge:** `channelFlow` creates Flows backed by channels for concurrent emission.

**Important:** Core operations (`send`, `receive`, `cancel`, `foreach`) don't require Async context — they only use ReentrantLock/Condition which work with all thread types. Builder functions (`produce`, `produceWith`, `channelFlow`) still require Async for `Async.fork()` and structured concurrency.

### Flows

Cold asynchronous data streams (similar to iterators but async).

- Terminal operation: `collect(collector)`
- Operators: `map`, `filter`, `take`, `drop`, etc.
- `buffer` operator enables concurrent producer/consumer via channels
- `channelFlow` creates Flows with concurrent emission capabilities
