/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.common.lower

import org.jetbrains.kotlin.backend.common.CommonBackendContext
import org.jetbrains.kotlin.backend.common.FileLoweringPass
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.evaluation.evaluate
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.visitors.IrTransformer
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.isJs
import org.jetbrains.kotlin.platform.isWasm

/**
 * Evaluates the same functions that [FirExpressionEvaluator] does, but inside bodies.
 */
class ConstEvaluationLowering(
    val context: CommonBackendContext,
    platform: TargetPlatform? = null,
) : FileLoweringPass {
    private val inlineConstTracker = context.configuration[CommonConfigurationKeys.INLINE_CONST_TRACKER]
    private val isFloatingPointOptimizationDisabled = platform.isJs() || platform.isWasm()

    override fun lower(irFile: IrFile) {
        irFile.transform(object : IrTransformer<Nothing?>() {
            override fun visitFunction(declaration: IrFunction, data: Nothing?): IrStatement {
                // It is useless to visit default accessor, we probably want to leave code there as it is
                if (declaration.origin == IrDeclarationOrigin.DEFAULT_PROPERTY_ACCESSOR) return declaration
                return visitDeclaration(declaration, data)
            }

            override fun visitExpression(expression: IrExpression, data: Nothing?): IrExpression {
                val superResult = super.visitExpression(expression, data)
                val evaluateResult = evaluate(
                    superResult,
                    irFile,
                    context.irBuiltIns,
                    inlineConstTracker,
                    isFloatingPointOptimizationDisabled = isFloatingPointOptimizationDisabled
                )
                return evaluateResult ?: superResult
            }
        }, null)
    }
}
