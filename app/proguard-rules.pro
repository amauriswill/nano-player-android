# ProGuard Rules for Nano Player
# Keeps Clean Architecture domain entities and reflection-free models safe from obfuscation issues
-keep class com.ipodnano.player.domain.model.** { *; }
-keep class com.ipodnano.player.domain.gateway.** { *; }
-keep class com.ipodnano.player.presentation.contract.** { *; }

# Optimize for low-end device CPU & memory constraints
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose
