# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Keep JNI methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep public API
-keep class com.mermes.core.bootstrap.MermesBootstrap { *; }
-keep class com.mermes.core.bootstrap.BootstrapResult { *; }
-keep class com.mermes.core.terminal.TerminalManager { *; }
-keep class com.mermes.core.terminal.TerminalSession { *; }
-keep class com.mermes.core.terminal.TerminalSessionCallback { *; }
-keep class com.mermes.core.terminal.ShellEnvironment { *; }
-keep class com.mermes.core.deb.DebInstaller { *; }
-keep class com.mermes.core.deb.DebInstallResult { *; }
-keep class com.mermes.core.Arch { *; }
-keep class com.mermes.core.MermesPaths { *; }

# Keep native libraries
-keep class com.mermes.core.bootstrap.NativeBootstrapLib { *; }
-keep class com.mermes.core.terminal.NativeTerminalLib { *; }
-keep class com.mermes.core.deb.NativeDebLib { *; }
-keep class com.mermes.core.utils.NativeUtils { *; }
