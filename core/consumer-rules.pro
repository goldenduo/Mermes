# Consumer ProGuard rules for mermes-core

# Keep JNI methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep public API
-keep class com.mermes.core.** { *; }
