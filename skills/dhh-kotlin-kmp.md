---
name: dhh-kotlin-kmp
description: Apply a strongly opinionated, high-performance architecture doctrine for Pure Kotlin and Kotlin Multiplatform (KMP), combining **Pure Clean Architecture** with **Emil Kowalski & Apple-grade UI/UX craftsmanship**. Enforces strict Clean Architecture layers (Domain, Data, Presentation), reactive Unidirectional Data Flow (UDF), zero-allocation performance hot paths, LRU bitmap caching, lifecycle-aware coroutines, and zero layout shift (CLS = 0) UI patterns.
---
# High-Performance Pure Clean Architecture & UX Doctrine (Kotlin & KMP)

Apply an uncompromising, enterprise-grade architecture and UI/UX philosophy tailored for **Kotlin and Kotlin Multiplatform (KMP)**. This doctrine marries strict **Pure Clean Architecture** separation of concerns with **world-class UI/UX performance craftsmanship** inspired by Emil Kowalski and Apple's human interface guidelines.

## Core Pillars:

### 1. Pure Clean Architecture (Uncompromising Layer Separation)
* **Domain Layer (Kotlin Pure - 100% Testable)**:
  * Contains Core Entities, Value Classes (`@JvmInline`), and Use Cases encapsulating distinct business rules.
  * Zero Android or platform framework dependencies. Completely pure Kotlin (`commonMain`).
* **Data Layer (Gateways & Repositories)**:
  * Implements Repository interfaces defined in Domain.
  * Manages local persistence (`MediaStore`, SQLDelight, DataStore) and remote data sources with robust error mapping (`Result<T>`).
* **Presentation Layer (State Containers & UI)**:
  * Unidirectional Data Flow (UDF) via StateFlow (`.asStateFlow()`) and atomic updates (`_state.update { ... }`).
  * Separation of stateful view models from stateless rendering UI components.

### 2. High-Performance Execution & Memory Management
* **Zero Allocation on Hot Paths**: Avoid object allocations inside animation frames, `onDraw` canvas loops, or high-frequency touch/scroll handlers.
* **Efficient Memory Caching**: Use bounded `LruCache` structures configured with `RGB_565` bitmaps for image/artwork assets to guarantee zero `OutOfMemoryError` across thousands of items.
* **Structured Concurrency**: Dispatcher injection across Use Cases and Repositories (`Dispatchers.IO`, `Dispatchers.Default`). Never hardcode dispatchers inside business logic classes.

### 3. Emil Kowalski & Apple-Grade UI/UX Craftsmanship
* **Micro-Interactions & Latency**: Every tap, transport action, and slider drag must provide immediate haptic feedback and instantaneous visual state change (zero perceived latency).
* **Zero Layout Shift (CLS = 0)**: Fixed constraints, tabular numbers (`tabular-nums`), and stable view metrics preventing any visual jitter or layout thrashing.
* **Aqua / Minimalist UI Aesthetics**: High-fidelity typography hierarchy, specular reflection highlights, specular gradients, and zero dead space.
