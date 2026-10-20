android-kotlin-native
Enterprise Android and Kotlin Multiplatform native engineering skill. Enforces Kotlin 2.x K2 compiler constraints, strict MVI/UDF state management, Jetpack Compose UI performance rules, lifecycle-aware state collection, and testing with Fakes and Turbine. Trigger this skill whenever the user asks to write, refactor, audit, or architect Android native apps, Kotlin code, Jetpack Compose screens, ViewModels, Room databases, Hilt DI, or Gradle configurations.

Instructions
Android and Kotlin Native Engineering Skill
This skill provides enterprise-grade guardrails for Android Native, Kotlin 2.x (K2), and Jetpack Compose development. It prevents common AI hallucinations and production bugs such as state race conditions, background battery drain, recomposition storms, and memory leaks.

When to Use
Writing or refactoring Jetpack Compose UI screens and components
Designing Android ViewModels, MVI/MVVM state containers, and UseCases
Setting up Navigation, Hilt/Koin Dependency Injection, Room, or Retrofit/Ktor
Auditing existing Android/Kotlin code for performance, memory leaks, or lifecycle issues
Writing unit and UI tests for Coroutines, Flows, and Compose
Core Engineering Rules

1. Kotlin 2.x and K2 Compiler Constraints
   Annotation Processing: Always use ksp instead of kapt for Hilt, Room, and Moshi/Kotlinx Serialization.
   Null Safety: Never use the unsafe !! operator in production code. Use smart casts (when (val s = state)), requireNotNull(), or early returns (?: return).
   Sealed Hierarchies: Model UiState, UiEvent, and UiEffect using sealed interface and data object for exhaustive when evaluation.
   Type Safety: Use @JvmInline value class for domain IDs and strongly typed primitives (e.g., @JvmInline value class UserId(val raw: String)).
   Compose Stability: Annotate UiState and domain models passed to Compose with @Immutable or @Stable, or use kotlinx.collections.immutable (ImmutableList) to guarantee skippable recompositions.
2. Architecture and State Management (Strict MVI / UDF)
   Atomic State Updates: Never mutate state via _state.value = _state.value.copy(...) as it introduces race conditions under concurrent coroutines. Always use atomic updates:
   _state.update { currentState -> currentState.copy(isLoading = false, items = newItems) }
   Encapsulation: Expose read-only StateFlow<UiState> using .asStateFlow(). Never expose MutableStateFlow or mutableStateListOf outside the ViewModel.
   Coroutine Scoping: Never use GlobalScope. Launch ViewModel coroutines in viewModelScope and inject CoroutineDispatcher (defaulting to Dispatchers.IO or Dispatchers.Default in repositories/use-cases, never hardcoding dispatchers inside classes so tests can override them).
   One-Off Effects: Handle navigation triggers, snackbars, or toasts via a Channel<UiEffect>(Channel.BUFFERED) exposed as receiveAsFlow(), or model them as consumable state properties with explicit clear events.
3. Jetpack Compose UI and Performance
   Lifecycle-Aware Collection: Always collect flows in Compose using collectAsStateWithLifecycle() from androidx.lifecycle:lifecycle-runtime-compose. Never use collectAsState() on Android, as it keeps upstream flows active in the background and drains battery.
   Stateless Composables and State Hoisting: Split screens into a stateful Route composable (which injects the ViewModel and collects state) and a stateless Screen composable (which takes uiState: UiState, onEvent: (UiEvent) -> Unit, and modifier: Modifier = Modifier as its first optional parameter).
   Lazy Layout Optimization: Every LazyColumn, LazyRow, or LazyVerticalGrid items() block must define both key and contentType to preserve scroll position and optimize composition reuse:
   LazyColumn(modifier = modifier) {
   items(
   items = state.items,
   key = { it.id },
   contentType = { "user_item" }
   ) { item ->
   UserRow(item = item, onClick = { onEvent(UiEvent.UserClicked(item.id)) })
   }
   }
   Zero Hardcoded Strings: Never pass raw string literals to Text() or contentDescription. Always use stringResource(R.string.label_id) or @StringRes resource identifiers so localization works properly.
   Derived State and Lambdas: Wrap frequent scroll or layout calculations in remember { derivedStateOf { ... } } to prevent unnecessary recompositions on every frame.
   Edge-to-Edge: Call enableEdgeToEdge() in ComponentActivity.onCreate and handle window insets properly using Scaffold padding values or Modifier.systemBarsPadding().
4. Type-Safe Navigation
   Serializable Routes: Use Jetpack Navigation Compose type-safe routes with @Serializable objects and data classes instead of hardcoded route strings:
   @Serializable data object HomeRoute
   @Serializable data class ProfileRoute(val userId: String)
   Lightweight Arguments: Pass only entity IDs in navigation arguments. Never pass complex parcelable/serializable domain objects in routes; fetch the data inside the destination ViewModel via SavedStateHandle.
5. Testing Standards
   Fakes Over Mocks: Prefer stateful Fake implementations of repositories and data sources over heavy mocking frameworks (MockK / Mockito) to test realistic behavior.
   Flow Testing: Use Turbine (app.cash.turbine:turbine) and runTest from kotlinx-coroutines-test to assert StateFlow emissions deterministically.
   Dispatcher Injection: Use StandardTestDispatcher or UnconfinedTestDispatcher via a custom JUnit4 TestWatcher (MainDispatcherRule) that calls Dispatchers.setMain().
   Banned Antipatterns Checklist
   Before outputting any Kotlin or Compose code, verify none of these banned patterns exist:

WRONG: val state = viewModel.state.collectAsState() -> RIGHT: val state by viewModel.state.collectAsStateWithLifecycle()
WRONG: _state.value = _state.value.copy(...) -> RIGHT: _state.update { it.copy(...) }
WRONG: LazyColumn { items(list) { ... } } -> RIGHT: LazyColumn { items(items = list, key = { it.id }, contentType = { ... }) { ... } }
WRONG: Text("Hello ${user.name}") -> RIGHT: Text(stringResource(R.string.greeting, user.name))
WRONG: kapt("com.google.dagger:hilt-compiler:...") -> RIGHT: ksp("com.google.dagger:hilt-compiler:...")
WRONG: navController.navigate("profile/${user.id}") -> RIGHT: navController.navigate(ProfileRoute(userId = user.id))
WRONG: GlobalScope.launch { ... } -> RIGHT: viewModelScope.launch { ... }
