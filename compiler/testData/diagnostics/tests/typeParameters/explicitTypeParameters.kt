// RUN_PIPELINE_TILL: BACKEND
// LANGUAGE: +ContextParameters
// DIAGNOSTICS: -UNUSED_PARAMETER
// RENDER_DIAGNOSTIC_ARGUMENTS

fun <T> returnOnly(): T = null!!

context(_: T)
fun <T> contextOnly() {}

fun <T> valueParameter(value: T): T = value

fun <T> T.receiver(): T = this

inline fun <reified T> reifiedValueParameter(value: T) {}

fun test() {
    val returned: String = <!CALLABLE_TYPE_PARAMETER_USAGE("{\"hasExplicitTypeArguments\":false,\"hasReifiedTypeParameter\":false,\"hasMaterializeLikeTypeParameter\":true,\"callableFqName\":\"returnOnly\",\"signature\":\"fun <T> returnOnly(): T\",\"firElementText\":\"returnOnly()\"}")!>returnOnly<!>()
    val returnedExplicit = <!CALLABLE_TYPE_PARAMETER_USAGE("{\"hasExplicitTypeArguments\":true,\"hasReifiedTypeParameter\":false,\"hasMaterializeLikeTypeParameter\":true,\"callableFqName\":\"returnOnly\",\"signature\":\"fun <T> returnOnly(): T\",\"firElementText\":\"returnOnly<String>()\"}")!>returnOnly<!><String>()

    context("") {
        <!CALLABLE_TYPE_PARAMETER_USAGE("{\"hasExplicitTypeArguments\":false,\"hasReifiedTypeParameter\":false,\"hasMaterializeLikeTypeParameter\":true,\"callableFqName\":\"contextOnly\",\"signature\":\"context(T) fun <T> contextOnly(): Unit\",\"firElementText\":\"contextOnly()\"}")!>contextOnly<!>()
        <!CALLABLE_TYPE_PARAMETER_USAGE("{\"hasExplicitTypeArguments\":true,\"hasReifiedTypeParameter\":false,\"hasMaterializeLikeTypeParameter\":true,\"callableFqName\":\"contextOnly\",\"signature\":\"context(T) fun <T> contextOnly(): Unit\",\"firElementText\":\"contextOnly<String>()\"}")!>contextOnly<!><String>()
    }

    valueParameter("")
    "".receiver()

    <!CALLABLE_TYPE_PARAMETER_USAGE("{\"hasExplicitTypeArguments\":false,\"hasReifiedTypeParameter\":true,\"hasMaterializeLikeTypeParameter\":false,\"callableFqName\":\"reifiedValueParameter\",\"signature\":\"fun <reified T> reifiedValueParameter(value: T): Unit\",\"firElementText\":\"reifiedValueParameter(\\"\\")\"}")!>reifiedValueParameter<!>("")
    <!CALLABLE_TYPE_PARAMETER_USAGE("{\"hasExplicitTypeArguments\":true,\"hasReifiedTypeParameter\":true,\"hasMaterializeLikeTypeParameter\":false,\"callableFqName\":\"reifiedValueParameter\",\"signature\":\"fun <reified T> reifiedValueParameter(value: T): Unit\",\"firElementText\":\"reifiedValueParameter<String>(\\"\\")\"}")!>reifiedValueParameter<!><String>("")
}

/* GENERATED_FIR_TAGS: checkNotNullCall, funWithExtensionReceiver, functionDeclaration, functionDeclarationWithContext,
inline, lambdaLiteral, localProperty, nullableType, propertyDeclaration, reified, stringLiteral, thisExpression,
typeParameter */
