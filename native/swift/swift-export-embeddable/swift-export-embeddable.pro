-target 1.8
-dontoptimize
-dontobfuscate
-dontprocesskotlinmetadata
-dontpreverify
-verbose

# Ignore classpath duplication from JVM
-dontnote sun.**

# These are IDE specific and shouldn't be reachable by Swift Export
-dontwarn org.jetbrains.kotlin.com.intellij.openapi.util.Iconable$IconFlags
-dontwarn org.jetbrains.kotlin.com.intellij.openapi.project.IndexNotReadyException
# These annotations are not retained at runtime, so these also shouldn't be reachable
-dontwarn org.jetbrains.kotlin.com.intellij.openapi.util.NlsSafe
-dontwarn org.jetbrains.kotlin.com.intellij.util.concurrency.annotations.RequiresReadLock

# IntelliJ optional deps not present in the standalone classpath
-dontwarn com.intellij.util.diff.*
-dontwarn org.jetbrains.kotlin.com.intellij.util.diff.*
-dontwarn gnu.trove.TObjectHashingStrategy
-dontwarn org.jaxen.**
-dontwarn dk.brics.automaton.*

# JDK Flight Recorder — optional telemetry, not available on all JDKs
-dontwarn jdk.jfr.**

# SSH support in JLine — optional, not used by Swift Export
-dontwarn org.jetbrains.kotlin.org.apache.sshd.**

# Apache Commons Compress — optional archiving support in IntelliJ
-dontwarn org.jetbrains.kotlin.org.apache.commons.compress.**

# Optional OpenTelemetry SDK (only the API is required at runtime)
-dontwarn org.jetbrains.kotlin.io.opentelemetry.sdk.**
-dontwarn org.jetbrains.kotlin.io.opentelemetry.extension.**
-dontwarn org.jetbrains.kotlin.com.intellij.platform.diagnostic.telemetry.**

# Annotation-only deps excluded from fatjar
-dontwarn org.jetbrains.annotations.**
-dontwarn org.intellij.lang.annotations.**

# kotlinx-serialization-json is provided as runtimeOnly (not embedded)
-dontwarn kotlinx.serialization.json.**

# javax.jms and javax.crypto — optional in log4j/JLine, not used by Swift Export
-dontwarn javax.jms.**
-dontwarn javax.crypto.**

# MethodHandle warnings from low-level JVM intrinsics (lz4, caffeine, etc.)
-dontwarn java.lang.invoke.MethodHandle

# Misc optional deps referenced in IntelliJ code paths not reachable by Swift Export
-dontwarn org.mozilla.universalchardet.**
-dontwarn org.jetbrains.kotlin.tooling.core.**
-dontwarn com.intellij.psi.util.PsiTreeUtilKt
-dontwarn org.jetbrains.kotlin.compilerRunner.ArgumentUtils
-dontwarn org.jetbrains.kotlin.javac.**
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn org.jetbrains.kotlin.org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn org.fusesource.jansi.internal.Kernel32
-dontwarn org.jetbrains.kotlin.org.jline.terminal.impl.jansi.win.**
-dontwarn android.annotation.SuppressLint

# Unresolved references in kotlinx-coroutines (debug agent, optional)
-dontwarn kotlinx.coroutines.debug.DebugProbes

# JDK Flight Recorder methods — jdk.jfr.Event subclasses (LLAbstractPhaseEvent and
# friends) lose begin/commit/end when jdk.jfr is absent. This dontwarn is scoped to
# the LL-FIR telemetry util package only; it does not suppress other LL-FIR warnings.
-dontwarn org.jetbrains.kotlin.analysis.low.level.api.fir.util.LL**

# Scripting plugins are on the runtime classpath (kotlin-scripting-compiler-embeddable),
# not embedded — the library-extends-program warnings are expected
-dontwarn org.jetbrains.kotlin.scripting.**
-dontwarn org.jetbrains.kotlin.assignment.plugin.**

# JS and Wasm backend classes are excluded from the fatjar (not used by Swift Export).
# Shared FIR/platform/CLI classes reference JS/Wasm types in unreachable platform-dispatch
# branches (session factories, checker registrations, pipeline phases). Suppress those
# dangling references — they cannot be reached from Swift Export's analysis-only entry points.
-dontwarn org.jetbrains.kotlin.fir.checkers.CheckersContainersKt
-dontwarn org.jetbrains.kotlin.fir.analysis.web.common.**
-dontwarn org.jetbrains.kotlin.fir.session.AbstractFirMetadataSessionFactory
-dontwarn org.jetbrains.kotlin.fir.session.AbstractFirMetadataSessionFactory$*
-dontwarn org.jetbrains.kotlin.fir.session.AbstractFirKlibSessionFactory
-dontwarn org.jetbrains.kotlin.fir.session.KlibIcCacheBasedSymbolProvider
-dontwarn org.jetbrains.kotlin.platform.CommonPlatforms
-dontwarn org.jetbrains.kotlin.platform.CommonPlatforms$*
-dontwarn org.jetbrains.kotlin.platform.js.**
-dontwarn org.jetbrains.kotlin.platform.wasm.**
-dontwarn org.jetbrains.kotlin.fir.session.FirJsSessionFactory
-dontwarn org.jetbrains.kotlin.fir.session.FirJsSessionFactory$*
-dontwarn org.jetbrains.kotlin.fir.session.FirWasmSessionFactory
-dontwarn org.jetbrains.kotlin.fir.session.FirWasmSessionFactory$*
-dontwarn org.jetbrains.kotlin.fir.session.KlibIcData
-dontwarn org.jetbrains.kotlin.fir.analysis.js.**
-dontwarn org.jetbrains.kotlin.fir.analysis.diagnostics.js.**
-dontwarn org.jetbrains.kotlin.fir.analysis.wasm.**
-dontwarn org.jetbrains.kotlin.js.**
-dontwarn org.jetbrains.kotlin.wasm.**
-dontwarn org.jetbrains.kotlin.analysis.low.level.api.fir.diagnostics.platform.LLJsCheckersConfiguration
-dontwarn org.jetbrains.kotlin.analysis.low.level.api.fir.diagnostics.platform.LLWasmCheckersConfiguration
-dontwarn org.jetbrains.kotlin.analysis.low.level.api.fir.diagnostics.platform.LLWasmCheckersConfiguration$*
-dontwarn org.jetbrains.kotlin.analysis.low.level.api.fir.diagnostics.platform.LLPlatformCheckersConfiguration$*
-dontwarn org.jetbrains.kotlin.analysis.low.level.api.fir.sessions.factory.components.LLJsSessionComponentRegistration
-dontwarn org.jetbrains.kotlin.analysis.low.level.api.fir.sessions.factory.components.LLWasmSessionComponentRegistration
-dontwarn org.jetbrains.kotlin.analysis.low.level.api.fir.sessions.factory.components.LLPlatformSessionComponentRegistration$*
-dontwarn org.jetbrains.kotlin.cli.common.FirSessionConstructionUtilsKt
-dontwarn org.jetbrains.kotlin.cli.common.FirSessionConstructionUtilsKt$*
-dontwarn org.jetbrains.kotlin.cli.pipeline.metadata.MetadataConfigurationUpdater

# Keep everything from Swift Export standalone
-keep public class org.jetbrains.kotlin.swiftexport.standalone.** { public *; }