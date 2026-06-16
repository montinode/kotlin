/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.evaluation

import org.jetbrains.kotlin.builtins.PrimitiveType
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.builtins.UnsignedType
import org.jetbrains.kotlin.incremental.components.InlineConstTracker
import org.jetbrains.kotlin.ir.IrBuiltIns
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrCompositeImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetObjectValueImpl
import org.jetbrains.kotlin.ir.irAttribute
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.getPrimitiveType
import org.jetbrains.kotlin.ir.types.getUnsignedType
import org.jetbrains.kotlin.ir.types.isString
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.visitors.IrVisitor
import org.jetbrains.kotlin.resolve.constants.evaluate.CompileTimeType
import org.jetbrains.kotlin.resolve.constants.evaluate.canEvalOp
import org.jetbrains.kotlin.resolve.constants.evaluate.evalBinaryOp
import org.jetbrains.kotlin.resolve.constants.evaluate.evalUnaryOp

var IrConst.wasInlined: Boolean? by irAttribute(copyByDefault = true)

fun evaluate(
    expression: IrExpression,
    irFile: IrFile,
    irBuiltIns: IrBuiltIns,
    inlineConstTracker: InlineConstTracker?,
    isFloatingPointOptimizationDisabled: Boolean,
): IrExpression? {
    return expression.accept(ConstInliner(irFile, irBuiltIns, inlineConstTracker, isFloatingPointOptimizationDisabled), null)
}

private class ConstInliner(
    private val irFile: IrFile,
    private val irBuiltIns: IrBuiltIns,
    private val inlineConstTracker: InlineConstTracker?,
    private val isFloatingPointOptimizationDisabled: Boolean,
) : IrVisitor<IrExpression?, Nothing?>() {
    private fun IrExpression.evaluateAsConst(): IrConst? = this.accept(this@ConstInliner, null) as? IrConst

    override fun visitElement(element: IrElement, data: Nothing?): IrExpression? = null

    override fun visitConst(expression: IrConst, data: Nothing?): IrConst = expression

    override fun visitCall(expression: IrCall, data: Nothing?): IrExpression? {
        return expression.correspondingProperty?.backingField?.let {
            expression.tryToInline(it)
        } ?: evaluateBuiltinCall(expression)
    }

    override fun visitGetField(expression: IrGetField, data: Nothing?): IrExpression? {
        val field = expression.symbol.owner
        return expression.tryToInline(field) ?: visitExpression(expression, data)
    }

    override fun visitWhen(expression: IrWhen, data: Nothing?): IrConst? {
        // Only fold the boolean `&&`/`||` operators, which are lowered to `when`.
        if (expression.origin != IrStatementOrigin.ANDAND && expression.origin != IrStatementOrigin.OROR) return null
        for (branch in expression.branches) {
            val condition = branch.condition.evaluateAsConst()?.value as? Boolean ?: return null
            if (condition) {
                return branch.result.evaluateAsConst()
            }
        }
        return null
    }

    override fun visitStringConcatenation(expression: IrStringConcatenation, data: Nothing?): IrConst? {
        val builder = StringBuilder()
        for (argument in expression.arguments) {
            val const = argument.evaluateAsConst() ?: return null
            builder.append(const.stringRepresentation() ?: return null)
        }
        return builder.toString().toIrConstOrNull(expression.type, expression.startOffset, expression.endOffset)
    }

    override fun visitTypeOperator(expression: IrTypeOperatorCall, data: Nothing?): IrConst? {
        return when (expression.operator) {
            IrTypeOperator.IMPLICIT_CAST, IrTypeOperator.IMPLICIT_NOTNULL -> expression.argument.evaluateAsConst()
            else -> null
        }
    }

    private fun evaluateBuiltinCall(expression: IrCall): IrConst? {
        if (!expression.isCompileTimeBuiltinCall(irBuiltIns)) return null

        val resultType = expression.type
        if (isFloatingPointOptimizationDisabled && resultType.isFloatOrDouble()) return null

        val name = expression.symbol.owner.name.asString()
        val operands = expression.arguments.map { argument ->
            val const = argument?.evaluateAsConst() ?: return null
            if (isFloatingPointOptimizationDisabled && const.type.isFloatOrDouble()) return null
            const
        }

        val computed: Any? = try {
            when (operands.size) {
                1 -> {
                    val type = operands[0].type.toCompileTimeType() ?: return null
                    val value = operands[0].value ?: return null
                    evaluateUnaryOperation(name, type, value)
                }
                2 -> {
                    val leftType = operands[0].type.toCompileTimeType() ?: return null
                    val rightType = operands[1].type.toCompileTimeType() ?: return null
                    val left = operands[0].value ?: return null
                    val right = operands[1].value ?: return null
                    evaluateBinaryOperation(name, leftType, left, rightType, right)
                }
                else -> return null
            }
        } catch (_: Exception) {
            // The operation would fail at runtime; leave the expression unfolded.
            return null
        }

        if (computed == null) return null
        return computed.toIrConstOrNull(resultType, expression.startOffset, expression.endOffset)
    }

    // Split the given expression into access to receiver (to keep semantic intact) and const value if applicable
    private fun IrExpression.tryToInline(field: IrField): IrExpression? {
        if (!field.canBeInlined()) return null

        val receiver = when (this) {
            is IrCall -> dispatchReceiver
            is IrGetField -> receiver
            else -> return null
        }

        val const = field.getInitializerAndReportInlining(this)
        if (receiver == null || receiver.shouldDropConstReceiver()) return const

        val fieldParent = field.parentAsClass
        val getObject = IrGetObjectValueImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, fieldParent.defaultType, fieldParent.symbol)
        when (this) {
            is IrCall -> this.dispatchReceiver = getObject
            is IrGetField -> this.receiver = getObject
        }

        return IrCompositeImpl(startOffset, endOffset, this.type, null, listOf(receiver, const))
    }

    private fun IrField.getInitializerAndReportInlining(original: IrExpression): IrConst {
        val const = this.initializer?.expression as IrConst
        inlineConstTracker?.reportOnIr(irFile, this, const)
        return const.shallowCopy().apply {
            startOffset = original.startOffset
            endOffset = original.endOffset
            wasInlined = true
        }
    }

    companion object {
        private val IrField.property: IrProperty?
            get() = this.correspondingPropertySymbol?.owner

        private val IrCall.correspondingProperty: IrProperty?
            get() = this.symbol.owner.correspondingPropertySymbol?.owner

        private val IrProperty?.isConst: Boolean
            get() = this?.isConst == true

        private fun IrExpression.shouldDropConstReceiver(): Boolean {
            return this is IrGetValue || this is IrGetObjectValue
        }

        fun IrField.isMarkedAsConst(): Boolean {
            val implicitConst = isFinal && isStatic && origin == IrDeclarationOrigin.IR_EXTERNAL_JAVA_DECLARATION_STUB && initializer != null
            return implicitConst || this.property.isConst
        }

        private fun IrField.canBeInlined(): Boolean {
            val property = this.property ?: return false
            val initializer = property.backingField?.initializer?.expression
            return this.isMarkedAsConst() && initializer is IrConst
        }

        private fun evaluateUnaryOperation(name: String, type: CompileTimeType, value: Any): Any? {
            return evalUnaryOp(name, type, value)
        }

        private fun evaluateBinaryOperation(
            name: String,
            leftType: CompileTimeType,
            left: Any,
            rightType: CompileTimeType,
            right: Any,
        ): Any? {
            // NB: some operations accept very general types, and due to the way the operation map works, we should up-cast the rhs type.
            val adjustedRightType = when (name) {
                "equals" -> CompileTimeType.ANY
                "plus" if leftType == CompileTimeType.STRING -> CompileTimeType.ANY
                else -> rightType
            }

            return evalBinaryOp(name, leftType, left, adjustedRightType, right)
        }

        private fun IrType.isFloatOrDouble(): Boolean =
            getPrimitiveType().let { it == PrimitiveType.FLOAT || it == PrimitiveType.DOUBLE }

        private fun IrType.toCompileTimeType(): CompileTimeType? = when (getPrimitiveType()) {
            PrimitiveType.BOOLEAN -> CompileTimeType.BOOLEAN
            PrimitiveType.CHAR -> CompileTimeType.CHAR
            PrimitiveType.BYTE -> CompileTimeType.BYTE
            PrimitiveType.SHORT -> CompileTimeType.SHORT
            PrimitiveType.INT -> CompileTimeType.INT
            PrimitiveType.LONG -> CompileTimeType.LONG
            PrimitiveType.FLOAT -> CompileTimeType.FLOAT
            PrimitiveType.DOUBLE -> CompileTimeType.DOUBLE
            null -> if (isString()) CompileTimeType.STRING else null
        }

        // Unsigned constants are stored with their signed representation in IrConst, so reinterpret them before rendering.
        private fun IrConst.stringRepresentation(): String? = when (type.getUnsignedType()) {
            UnsignedType.UBYTE -> (value as? Byte)?.toUByte()?.toString()
            UnsignedType.USHORT -> (value as? Short)?.toUShort()?.toString()
            UnsignedType.UINT -> (value as? Int)?.toUInt()?.toString()
            UnsignedType.ULONG -> (value as? Long)?.toULong()?.toString()
            null -> value.toString()
        }

        private fun IrCall.isCompileTimeBuiltinCall(irBuiltIns: IrBuiltIns): Boolean {
            val owner = this.symbol.owner

            if (!owner.fromStdlib()) return false

            val receiverType = owner.parameters.getOrNull(0)?.type?.toCompileTimeType()
            val firstArgType = owner.parameters.getOrNull(1)?.type?.toCompileTimeType()

            val inBuiltinMap = canEvalOp(
                callableId = owner.callableId,
                typeA = receiverType,
                typeB = firstArgType
            )
            return inBuiltinMap
        }

        private fun IrDeclaration.fromStdlib(): Boolean {
            return this.getPackageFragment().packageFqName.startsWith(StandardNames.BUILT_INS_PACKAGE_NAME)
        }
    }
}
