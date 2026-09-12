# Learning JVM Internals — Working Notes

*A running log of hands-on experiments, findings, and explanations, building toward a deeper understanding of JVM internals (bytecode, GC, JIT). Intended as raw material for a future write-up.*

---

## Glossary (build this up as we go)

- **Constant pool** — a table embedded in every `.class` file holding symbolic references (class names, method signatures, string/numeric literals). Bytecode instructions reference entries by index rather than embedding data inline. Resolved (bound to real memory) lazily at runtime, then cached.
- **Eden** — the space where all new objects are first allocated.
- **Survivor (From/To)** — holding area for objects that survived at least one young GC.
- **Tenured / Old Generation** — where long-lived, repeatedly-surviving objects get promoted.
- **Young GC** — collects only Eden + Survivor. Cheap, frequent.
- **Full GC** — collects the entire heap including Old Gen. Expensive, should be rare in a healthy app.
- **Region (G1)** — G1 divides the whole heap into many fixed-size chunks (e.g. 1MB) that can be dynamically labeled Eden/Survivor/Old/Free between cycles, rather than fixed blocks like Serial GC.
- **Generational hypothesis** — the empirical basis for all of the above: most objects die young; objects that survive tend to keep surviving. Justifies not scanning the whole heap on every GC.

---

## Experiment 1: Reading bytecode with `javap`

**Goal:** see what `javac` actually generates from simple source.

**Code:** `HelloWorld.java` — println a string, then an int.

**Command:**
```
javap -c -p -v HelloWorld.class
```

**Findings:**
- Every class gets a synthesized no-arg constructor if you don't write one — it just calls `super()` (`Object.<init>()`).
- `System.out.println("...")` compiles to 3 constant-pool-driven steps: `getstatic` (fetch the field), `ldc` (load the string constant), `invokevirtual` (call the resolved method overload).
- Overloaded methods (`println(String)` vs `println(int)`) get **separate constant pool `Methodref` entries** — overload resolution happens at compile time, based on the argument's *static* type, not runtime type.
- Small int constants (-1 to 5) get dedicated single-byte instructions (`iconst_5`) instead of going through the constant pool via `ldc`.
- `major version` in the class file header identifies the JDK that compiled it (69 = Java 25, 67 = Java 23, etc.) — this is what triggers `UnsupportedClassVersionError` when running newer bytecode on an older JVM.
- `LocalVariableTable`/`LineNumberTable` are pure debug metadata for tools (debuggers, stack traces) — the JVM itself only cares about local variable *slot numbers*, not names.

**Constant pool mechanics:**
- Entries reference other entries by index (e.g. `Methodref #2.#3` → `#2` = Class, `#3` = NameAndType → which itself points to two more `Utf8` entries for name + descriptor). Heavy indirection enables deduplication.
- At runtime: symbolic references get *resolved* (often lazily, on first use) to actual memory addresses, then the pool entry is patched in-place so future uses skip resolution — one reason first invocation of a method can be slower than later ones.
- String literals referenced via the constant pool get automatically interned into the JVM's global string pool.

**Still to try:** string concatenation with `+` and `invokedynamic`, overload resolution when static type ≠ runtime type, loops.

---

## Experiment 2: GC logs — Serial vs G1

**Goal:** see actual GC behavior on an allocation-heavy program, and compare collectors across environments.

**Code:** `GCDemo.java` — loop 2M times, allocate a throwaway `byte[1024]` each time, occasionally keep one alive in a `List`.

**Command:**
```
java -Xmx32m -cp target/classes '-Xlog:gc*:file=gc.log:time,uptime,level,tags' com.cypherlabs.jvm.internals.experiments.GCDemo
```

**Flag notes:**
- `-Xmx32m` — caps heap at 32MB deliberately small, to force frequent GCs we can actually observe (default heap sizing based on machine RAM would likely be too large to see much).
- `-Xlog:gc*:file=gc.log:time,uptime,level,tags` — unified JVM logging: `gc*` = tag selector (all GC-related subsystem tags), `file=gc.log` = write to a file instead of stdout, `time,uptime,level,tags` = which metadata decorators to prefix each line with.
- On Linux, single-CPU sandbox → JVM ergonomics picked **Serial GC** automatically.
- On Mac, 8-core → JVM ergonomics picked **G1 GC** automatically. Same JVM, different default collector, purely based on available CPUs.

**⚠️ Finding #1 — first run on Mac showed ZERO GC events**, despite ~2GB of theoretical total allocation over the run. Initial hypothesis: JIT escape analysis eliminated the allocation entirely (dead code elimination), since `junk` never escapes the loop iteration.

**Investigation to confirm the hypothesis, and a correction:**
- `-XX:+PrintCompilation` showed the loop went through: interpreted → OSR tier 3 (with profiling) → OSR tier 4/C2 (full optimization, ~80ms in) → an uncommon-trap deoptimization shortly after (C2's first optimistic assumption got invalidated, then presumably recompiled).
- Tried `-XX:+PrintEliminateAllocations` for direct confirmation — **unavailable**: it's a "develop"-only flag, requires a special debug JVM build not shipped in normal JDKs (different from "diagnostic" flags, which just need `-XX:+UnlockDiagnosticVMOptions` and work fine on normal JVMs).
- Used **JFR** instead (`-XX:StartFlightRecording=filename=recording.jfr,settings=profile,dumponexit=true`) to directly observe real allocation events.
- **Result: JFR recording (a run WITH JFR attached) showed 9 real Young GCs actually occurred**, all clustered in a ~21ms window early in the run, with `jdk.ObjectAllocationSample` events confirming real `byte[]` allocations happening in that exact window.

**Corrected conclusion:** the original claim "zero allocations, ever" was wrong. The accurate story: the loop allocates for real during interpreted execution and early (tier 3) compilation; once C2 (tier 4) compiles with escape analysis and proves the allocation is dead, further allocations are eliminated — from that point on, no more GC activity. The magnitude of the "real allocation window" (9 GCs here vs. 0 in the earlier plain `-Xlog:gc*` run) varies because **attaching JFR itself perturbs JVM timing** (observer effect) — different measurement tools can produce different warmup windows for the same program. Lesson: don't trust a single measurement's absolute numbers; corroborate with more than one tool when something looks surprising.

**Bonus finding — adaptive tenuring observed live:** one of the 9 GCs showed `tenuringThreshold = 1` (vs. the default 15 seen on the others) — G1 dynamically lowering the tenuring threshold under Survivor-space pressure, forcing quicker promotion to Old Gen. Direct, real evidence of the "dynamic tenuring" mechanism discussed conceptually earlier.

**Tools/flags catalogued from this investigation:**
- `-XX:+PrintCompilation` — one line per compilation event (method or OSR loop), shows timestamp, tier, and whether OSR (`%`) or deopt (`made not entrant: ...`). Does NOT show per-statement optimization detail or actual machine code.
- `-XX:+UnlockDiagnosticVMOptions` — gate for diagnostic-tier flags (safe on normal JVMs, just hidden by default).
- `-XX:+PrintEliminateAllocations` — a "develop"-tier flag, only works on special debug JVM builds, NOT available on ordinary production JDKs.
- `-XX:StartFlightRecording=filename=X.jfr,settings=profile,dumponexit=true` — records a JFR profile; `dumponexit=true` is important for short-lived programs to ensure the recording actually flushes to disk.
- `jfr summary X.jfr` — lists every event type captured and counts; use this FIRST to see what's actually in a recording before grepping blindly.
- `jfr print --events <EventType> X.jfr` — prints full detail for a specific event type.

**Still to try:** rerun `GCDemo` with the `sink`-field fix (forcing the allocation to be observably used) to compare against this "eliminated" version — expect many more real, sustained GCs throughout the whole run, not just a brief early burst.

---

---

## Concept deep-dive: object aging, promotion, Young vs Full GC triggers

**When does Eden → Survivor happen?**
Not continuously monitored — decided entirely at Young GC time. When Eden fills and an allocation fails, GC triggers, walks from GC roots (thread stacks, static fields) to find reachable objects in Eden, and copies each reachable one into a Survivor space. Unreached objects are simply left behind and Eden is wiped/reused — no per-object delete needed, which is why this is cheap.

**Is survival count tracked? Where?**
Yes — an **age counter** stored directly in the object's header (part of the "mark word" every object carries, alongside hash code / lock state). Each time the object survives a young GC, its age increments. Once age crosses `-XX:MaxTenuringThreshold` (default 15; HotSpot can also promote earlier via "dynamic tenuring" if Survivor space is under pressure), the object is promoted straight to Old Gen instead of being copied to Survivor again.

**Young GC vs Full GC — trigger conditions:**
- **Young GC** — Eden fills, an allocation fails → triggers. Only scans Eden + Survivor. Cheap, frequent, normal.
- **Full GC** — Old Gen (or Metaspace) can't satisfy a promotion or large allocation → triggers. Scans/compacts the *entire* heap. Expensive, should be rare.

**Common causes of frequent Full GCs (the real-world problem case):**
- Heap too small for the actual live working set.
- Memory leak — objects technically still reachable (forgotten cache entries, listeners, static collections) but functionally dead, accumulating in Old Gen.
- High promotion rate — objects live just long enough to get promoted prematurely, filling Old Gen fast.
- Wrong GC algorithm choice for the workload/heap size/latency requirements.

**Core mental model:** GC is purely *reactive to allocation failure*, not time-based or polling. Young GC fires on Eden exhaustion; Full GC fires on Old Gen/Metaspace exhaustion. Aging/promotion/region-selection are bookkeeping done *during* these trigger events, not independent background processes.

**Correction/precision on "cheap" young GC:** copying is NOT free — it's real work (copy + reference updates) for every surviving object. "Cheap" specifically means cost is proportional to the number of *live/surviving* objects, not the size of Eden or the amount of garbage. A copying collector never has to individually visit or bookkeep garbage objects (contrast with mark-sweep, used for Old Gen, where cost scales with the *amount of garbage* found — tracking each dead object into a free list, dealing with fragmentation). Since young gen is specifically where the generational hypothesis predicts "almost everything here is garbage," copying (cost ∝ small survivor set) beats sweeping (cost ∝ large garbage set) for this region specifically.

**Promotion failure → Full GC (the precise trigger):** Full GC isn't simply "young GC ran, then separately a new allocation failed." It's specifically: *during* a Young GC, when the collector tries to promote a surviving/aged object into Old Gen and Old Gen has no room — that's a "promotion failure," and it escalates directly into a Full GC.

**Humongous objects (G1-specific gotcha):** An object larger than 50% of the configured region size bypasses Eden entirely and gets allocated directly into contiguous regions that behave like Old Gen ("humongous allocation"). This means large objects can fill Old Gen fast without ever passing through the young-gen filtering process — a well-known real-world cause of unexpectedly frequent Full GCs even with no apparent "leak." Region size (`-XX:G1HeapRegionSize`) directly controls the humongous threshold — increasing region size reduces how often objects get classified as humongous.

---

## Core definitions (precise, no hand-waving)

**Garbage** — an object is garbage if it is *unreachable*: starting from GC roots (thread-local stack variables, static fields, active JNI refs) and following every reference field, you can never reach it. One live reference chain to it means it is NOT garbage, regardless of whether the code "looks like" it's done with it.

**Bump-pointer allocation (why `new` in Eden is so fast)** — Eden is one contiguous block of memory. The JVM tracks a single number: the address of the next free byte ("top"/allocation pointer). Allocating an object = check it fits before Eden's boundary, hand back the current pointer, then just add the object's size to the pointer. No searching, no free-list lookup — this is often faster than C's `malloc`. After a Young GC copies survivors out, Eden's pointer is simply reset to the start — the "cleanup" is one pointer reset, not per-object deletion, because leftover garbage bytes will just get silently overwritten by future allocations (nothing will ever reference them again, per the definition of garbage).

**Why bump-pointer/copying avoids fragmentation** — Fragmentation = free memory existing but scattered into small non-contiguous gaps, so a large-enough request can't find a slot even though total free space would suffice. Copying collectors always relocate every survivor elsewhere, leaving *nothing* behind in Eden — so Eden always resets to one full contiguous free block. There's no "delete an object, leave a hole" step, because nothing is ever deleted in place.

**Mark-sweep** — a different GC algorithm (used for Old Gen). Two phases: (1) **Mark** — walk from GC roots, flag every reachable object live. (2) **Sweep** — walk the whole region; for each unmarked (garbage) object, add its space to a free list, but do NOT move surviving objects. Because survivors stay exactly where they were, the result is `[live][gap][live][live][gap][gap][live]...` — fragmentation, directly caused by not relocating anything. Contrast with copying: relocating survivors is precisely what avoids this.

**Compaction** — an optional third phase on top of mark-sweep: slide all live objects together to eliminate the gaps between them, at the cost of extra work (every reference to a moved object must be updated, same challenge copying collectors already handle for young gen). Full GC = mark-sweep-**compact** across the whole heap.

**Full GC gives no space guarantee** — compaction only eliminates fragmentation and reclaims space occupied by garbage; it cannot create space if genuinely *live* data has simply outgrown the heap. If a Full GC still can't free enough contiguous space: JVM expands the heap if below `-Xmx`, otherwise throws `OutOfMemoryError: Java heap space`. There's also a "GC overhead limit exceeded" safeguard — if the JVM detects it's burning nearly all CPU time on repeated Full GCs while reclaiming very little each time (thrashing), it proactively throws OOM rather than continuing to spin.

**Humongous objects (G1) — handled as a genuinely separate path, not just "a big region":**
- Object size > 50% of configured region size → allocated into one or more whole *consecutive* regions reserved just for it (rounds up — a 3.5-region object consumes 4 whole regions).
- Treated like Old Gen for reclamation: cleaned up during Old Gen / concurrent marking cycles, not ordinary Young GC.
- Because relocating something this large is expensive, G1 mostly doesn't move humongous objects — during concurrent marking it just checks reachability; if unreachable, the regions are reclaimed as free directly (no copy needed). If still live, it just stays put.
- Practical consequence: humongous objects skip the cheap young-gen copying-collector benefits entirely, going straight to the more expensive/less-frequent Old-Gen-style path — a well-known, hard-to-diagnose source of GC pressure from allocation patterns that look innocuous in code.

---

## Concept deep-dive: the From/To Survivor design (the piece missing above)

**Correction to earlier explanation:** young gen isn't "Eden + one Survivor space" — it's "Eden + Survivor **From** + Survivor **To**." Only one Survivor space is active (holding objects) at any time; the other is always kept empty, reserved for the next cycle.

**How a young GC actually uses both:** GC roots are walked once per cycle. Any reachable object found in **either Eden or the current From-Survivor space** gets evacuated in the same pass:
- Not yet aged out → copied into the currently-empty **To-Survivor** space, age incremented.
- Aged past `-XX:MaxTenuringThreshold` → promoted directly to Old Gen instead.

No separate detection mechanism or algorithm is needed for Survivor — it's the same reachability walk and the same copy operation as Eden, just with two source spaces (Eden, From) feeding two destination spaces (To, Old) in one cycle.

**Why this avoids fragmentation, same as Eden:** because *everything* reachable is evacuated out of Eden and From every single cycle, both end up completely empty afterward (not "empty except leftover survivors" — genuinely empty). Both can therefore reset their bump-allocation pointer to the start, same gap-free logic as isolated Eden.

**Why two Survivor spaces instead of one:** with only one Survivor space, you couldn't bump-pointer-reset it after a GC, because it would still contain last cycle's valid survivors mixed with this cycle's new arrivals — no clean way to tell "already there" from "newly copied in," and no way to safely wipe it. Having a second, always-empty destination space to copy into preserves the fast, gap-free, bump-pointer allocation property. After each cycle, the old From (now fully drained, empty) becomes the new To; the old To (now holding this cycle's survivors) becomes the new From. The From/To labels literally swap every single young GC cycle — they aren't fixed regions, they're rotating roles.

---

## Concept deep-dive: why doesn't the JIT eliminate the allocation immediately? (tiered compilation's cost/benefit logic)

**Why not in `javac`?** `javac` is a fast, deliberately non-optimizing translator — no dataflow/escape analysis. It has zero runtime information (which methods run once vs. millions of times) and needs to compile fast on every save/build, so it defers all such analysis to the JVM at runtime.

**Why doesn't the JVM do it immediately either, even though the pattern "looks obvious"?** Escape analysis isn't free — it's a real, general-purpose algorithm (build control-flow graph, track every place a reference could be stored/passed, potentially analyze/inline called methods to rule out escapes there too) that has to run the same way regardless of whether the method turns out to be simple or complex. It "looks obvious" to a human reading 4 lines with full context, but the compiler can't special-case "this one's easy" — running the full analysis is the only way to discover that. Running this expensive analysis on *every* method immediately (most of which run only a handful of times and never benefit) would make JVM startup dramatically slower for negligible payoff.

**Core design principle:** HotSpot refuses to guess upfront which code deserves expensive optimization — it observes first, then commits. Tiered compilation pipeline: interpreter (zero cost) → tier 3 (cheap compile + profiling, "is this actually hot?") → tier 4/C2 (expensive analysis — escape analysis, aggressive inlining — only for code proven hot by having actually run enough iterations/invocations).

**This directly explains the "9 allocations" number:** those are the unavoidable cost of the observation period itself. The JVM cannot know a loop deserves expensive escape analysis until it's watched it run long enough to justify the investment — and during that necessary observation window (interpreted + early-tier compiled execution), real allocations genuinely happen. This isn't inefficiency to fix — it's the fundamental price of a "measure before optimizing" design.

---

## Concept deep-dive: scalar replacement vs. plain dead-code elimination (the `sink` experiment)

**Initial (incorrect) claim tested:** adding `sink += junk[0]` would force the JIT to keep a real `byte[1024]` allocation, since `sink`'s final value now depends on `junk`'s contents.

**Empirical result:** unchanged — still exactly 9 real GCs, clustered in the same early ~20ms warmup window, identical to the version with no `sink` at all. Confirmed via JFR (`jfr summary`, `jfr print --events jdk.YoungGarbageCollection`).

**Why the claim was wrong — the precise distinction:** the JVM only guarantees *observable behavior* is preserved, not that objects named in source code must physically exist in memory. The correct test for whether an allocation survives isn't "is a value derived from this object used somewhere" — it's "**does anything require this object's identity** (a single addressable thing at one memory location), as opposed to just the individual values inside it?" In the `sink` version, only one scalar byte value (`junk[0]`) is ever read, immediately, in the same iteration it was written, and never again. The array's identity as a whole object is never needed. This lets the compiler perform **scalar replacement**: track that one value as a plain scalar/local variable, and skip allocating the array object entirely, while still producing byte-for-byte identical program output — including with a genuinely varying (non-constant) value, e.g. `(byte) iteration`, not just a fixed constant.

**What would actually force a real, non-scalarizable allocation:** publishing the object's *reference/identity* somewhere external and inspectable outside the current iteration — e.g. `static byte[] escapedRef; ... escapedRef = junk;` — because now something outside the loop needs to be able to point at one real, addressable object, potentially at a different time than when it was written. There's no scalar substitute for "a live object other code might dereference later." (Not yet empirically verified in this investigation — next experiment to run.)

**Tooling note:** direct compiler confirmation of scalar replacement (equivalent to `-XX:+PrintEliminateAllocations`) requires a debug-only JVM build, unavailable to us. JFR-based indirect measurement (GC counts + allocation sample timing/clustering) is the practical verification method on a normal JDK.

---

## Open questions / to explore later
- What does JIT-compiled assembly actually look like for a hot method (`-XX:+PrintAssembly`)?
- How does G1 decide *which* regions to collect first ("garbage first" heuristic)?
- What does a Full GC log entry look like, and how does it differ structurally from a Young GC entry?