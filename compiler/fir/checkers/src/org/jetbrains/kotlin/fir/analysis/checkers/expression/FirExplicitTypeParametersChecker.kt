/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis.checkers.expression

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.isExplicit
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirErrors
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
import org.jetbrains.kotlin.fir.expressions.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirTypeParameterSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirValueParameterSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.ConeTypeParameterType
import org.jetbrains.kotlin.fir.types.contains
import org.jetbrains.kotlin.fir.types.renderReadable
import org.jetbrains.kotlin.text

object FirExplicitTypeParametersChecker : FirQualifiedAccessExpressionChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirQualifiedAccessExpression) {
        val symbol = expression.toResolvedCallableSymbol() ?: return
        val typeParameterUsage = symbol.typeParameterUsage()
        if (!typeParameterUsage.shouldReport) return

        reporter.reportOn(
            expression.source,
            FirErrors.CALLABLE_TYPE_PARAMETER_USAGE,
            symbol.diagnosticPayload(
                hasExplicitTypeArguments = expression.typeArguments.any { it.isExplicit },
                typeParameterUsage = typeParameterUsage,
                firElementText = expression.source?.text?.toString(),
            ),
        )
    }
}

private data class TypeParameterUsage(
    val hasReifiedTypeParameter: Boolean,
    val hasMaterializeLikeTypeParameter: Boolean,
) {
    val shouldReport: Boolean
        get() = hasReifiedTypeParameter || hasMaterializeLikeTypeParameter
}

private fun FirCallableSymbol<*>.typeParameterUsage(): TypeParameterUsage {
    var hasReifiedTypeParameter = false
    var hasMaterializeLikeTypeParameter = false

    for (typeParameter in typeParameterSymbols) {
        hasReifiedTypeParameter = hasReifiedTypeParameter || typeParameter.isReified
        hasMaterializeLikeTypeParameter = hasMaterializeLikeTypeParameter || typeParameter.isUsedInReturnOrContextOnly(this)
    }

    return TypeParameterUsage(
        hasReifiedTypeParameter = hasReifiedTypeParameter,
        hasMaterializeLikeTypeParameter = hasMaterializeLikeTypeParameter,
    )
}

private fun FirCallableSymbol<*>.diagnosticPayload(
    hasExplicitTypeArguments: Boolean,
    typeParameterUsage: TypeParameterUsage,
    firElementText: String?,
): String {
    return buildString {
        append('{')
        append("\"hasExplicitTypeArguments\":")
        append(hasExplicitTypeArguments)
        append(",\"hasReifiedTypeParameter\":")
        append(typeParameterUsage.hasReifiedTypeParameter)
        append(",\"hasMaterializeLikeTypeParameter\":")
        append(typeParameterUsage.hasMaterializeLikeTypeParameter)
        append(",\"callableFqName\":")
        appendJsonString(callableId?.asSingleFqName()?.asString() ?: "<local>")
        append(",\"signature\":")
        appendJsonString(renderSignature())
        append(",\"firElementText\":")
        appendJsonString(firElementText ?: "<unknown>")
        append('}')
    }
}

private fun FirCallableSymbol<*>.renderSignature(): String {
    val typeParameters = typeParameterSymbols.joinToString(prefix = "<", postfix = "> ") { typeParameter ->
        buildString {
            if (typeParameter.isReified) append("reified ")
            append(typeParameter.name.asString())
        }
    }.takeIf { typeParameterSymbols.isNotEmpty() }.orEmpty()
    val contextParameters = contextParameterSymbols.joinToString(prefix = "context(", postfix = ") ") { it.renderSignature() }
        .takeIf { contextParameterSymbols.isNotEmpty() }
        .orEmpty()
    val receiver = receiverParameterSymbol?.resolvedType?.renderReadable()?.let { "$it." }.orEmpty()
    val name = callableId?.callableName?.asString() ?: "<anonymous>"

    return when (this) {
        is FirFunctionSymbol<*> -> {
            val valueParameters = valueParameterSymbols.joinToString { it.renderSignature() }
            "${contextParameters}fun $typeParameters$receiver$name($valueParameters): ${resolvedReturnType.renderReadable()}"
        }
        else -> "${contextParameters}val $typeParameters$receiver$name: ${resolvedReturnType.renderReadable()}"
    }
}

private fun FirValueParameterSymbol.renderSignature(): String {
    val renderedType = resolvedReturnType.renderReadable()
    return if (name.isSpecial) renderedType else "${name.asString()}: $renderedType"
}

private fun StringBuilder.appendJsonString(value: String) {
    append('"')
    value.forEach { char ->
        when (char) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            in '\u0000'..'\u001F' -> append("\\u").append(char.code.toString(16).padStart(4, '0'))
            else -> append(char)
        }
    }
    append('"')
}

private fun FirTypeParameterSymbol.isUsedInReturnOrContextOnly(symbol: FirCallableSymbol<*>): Boolean {
    fun ConeKotlinType.containsThisTypeParameter(): Boolean {
        return contains { type ->
            (type as? ConeTypeParameterType)?.lookupTag?.typeParameterSymbol == this@isUsedInReturnOrContextOnly
        }
    }

    if (symbol.receiverParameterSymbol?.resolvedType?.containsThisTypeParameter() == true) return false
    if (symbol is FirFunctionSymbol<*> && symbol.valueParameterSymbols.any { it.resolvedReturnType.containsThisTypeParameter() }) return false

    return symbol.resolvedReturnType.containsThisTypeParameter() ||
            symbol.contextParameterSymbols.any { it.resolvedReturnType.containsThisTypeParameter() }
}
