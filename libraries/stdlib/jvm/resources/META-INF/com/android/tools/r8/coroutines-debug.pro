# Effectively disables debug probes usage on Android
-assumenosideeffects class kotlin.coroutines.jvm.internal.DebugProbesKt {
    boolean IS_ANDROID return true;
    boolean hasNoProbes return true;
}
