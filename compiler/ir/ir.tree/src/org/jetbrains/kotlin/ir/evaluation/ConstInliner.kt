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
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.irAttribute
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.visitors.IrVisitor
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.resolve.constants.evaluate.CompileTimeType
import org.jetbrains.kotlin.resolve.constants.evaluate.canEvalOp
import org.jetbrains.kotlin.resolve.constants.evaluate.evalBinaryOp
import org.jetbrains.kotlin.resolve.constants.evaluate.evalUnaryOp
import org.jetbrains.kotlin.utils.addToStdlib.runIf

var IrConst.wasInlined: Boolean? by irAttribute(copyByDefault = true)

/**
 * Evaluates the given IR expression using constant folding.
 * This function returns either
 *  - [IrConst], if exression can be completly folded;
 *  - [IrComposite], if expression is folded, but can't be completly eliminated because it has side effects;
 *  - null, if the expression can't be folded.
 */
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
        val property = expression.correspondingProperty
        val field = property?.backingField
        return when {
            field != null -> {
                if (!field.canBeInlined()) return evaluateBuiltinCall(expression)

                val const = field.getInitializerAndReportInlining(expression)
                val receiver = expression.dispatchReceiver
                if (receiver == null || receiver.shouldDropConstReceiver()) return const

                IrCompositeImpl(expression.startOffset, expression.endOffset, expression.type, null, listOf(receiver, const))
            }
            property != null -> {
                if (!property.isConst) return evaluateBuiltinCall(expression)

                val const = (property.getter?.body?.statements?.singleOrNull() as? IrConst)?.shallowCopy()
                    ?: return evaluateBuiltinCall(expression)
                val receiver = expression.dispatchReceiver
                if (receiver == null || receiver.shouldDropConstReceiver()) return const

                IrCompositeImpl(expression.startOffset, expression.endOffset, expression.type, null, listOf(receiver, const))
            }
            else -> evaluateBuiltinCall(expression)
        }
    }

    override fun visitGetField(expression: IrGetField, data: Nothing?): IrExpression? {
        val field = expression.symbol.owner
        if (!field.canBeInlined()) return null

        val const = field.getInitializerAndReportInlining(expression)
        val receiver = expression.receiver
        if (receiver == null || receiver.shouldDropConstReceiver()) return const

        return IrCompositeImpl(expression.startOffset, expression.endOffset, expression.type, null, listOf(receiver, const))
    }

    override fun visitStringConcatenation(expression: IrStringConcatenation, data: Nothing?): IrConst? {
        val builder = StringBuilder()
        for (argument in expression.arguments) {
            val const = argument.evaluateAsConst() ?: return null
            builder.append(const.getCastedValue() ?: return null)
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

        val owner = expression.symbol.owner
        val name = owner.name.asString()
        val operands = expression.arguments.mapIndexed { index, argument ->
            val const = argument?.evaluateAsConst()
            // This hack is required because on fir2ir level defaults are represented as stubs.
                ?: runIf(argument == null && owner.parameters[index].defaultValue != null && name == "trimMargin") {
                    IrConstImpl.string(UNDEFINED_OFFSET, UNDEFINED_OFFSET, owner.parameters[index].type, "|")
                }
                ?: return null
            if (isFloatingPointOptimizationDisabled && const.type.isFloatOrDouble()) return null
            const
        }

        val computed: Any? = try {
            when (operands.size) {
                1 -> {
                    val type = owner.parameters[0].type.toCompileTimeType() ?: return null
                    val value = operands[0].getCastedValue() ?: return null
                    evaluateUnaryOperation(name, type, value)
                }
                2 -> {
                    val leftType = owner.parameters[0].type.toCompileTimeType() ?: return null
                    val rightType = owner.parameters[1].type.toCompileTimeType() ?: return null
                    val left = operands[0].getCastedValue() ?: return null
                    val right = operands[1].getCastedValue() ?: return null
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

        private val IrDeclarationWithName.callableId: CallableId?
            get() {
                return when (val parent = this.parent) {
                    is IrClass -> parent.classId?.let { CallableId(it, name) }
                    is IrPackageFragment -> CallableId(parent.packageFqName, name)
                    else -> null
                }
            }

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

        private fun IrType.toCompileTimeType(): CompileTimeType? {
            if (this.isAny() || type.isNullableAny()) return CompileTimeType.ANY
            return when (getPrimitiveType()) {
                PrimitiveType.BOOLEAN -> CompileTimeType.BOOLEAN
                PrimitiveType.CHAR -> CompileTimeType.CHAR
                PrimitiveType.BYTE -> CompileTimeType.BYTE
                PrimitiveType.SHORT -> CompileTimeType.SHORT
                PrimitiveType.INT -> CompileTimeType.INT
                PrimitiveType.LONG -> CompileTimeType.LONG
                PrimitiveType.FLOAT -> CompileTimeType.FLOAT
                PrimitiveType.DOUBLE -> CompileTimeType.DOUBLE
                null -> when (getUnsignedType()) {
                    UnsignedType.UBYTE -> CompileTimeType.UBYTE
                    UnsignedType.USHORT -> CompileTimeType.USHORT
                    UnsignedType.UINT -> CompileTimeType.UINT
                    UnsignedType.ULONG -> CompileTimeType.ULONG
                    null -> when {
                        isString() -> CompileTimeType.STRING
                        else -> null
                    }
                }
            }
        }

        private fun IrCall.isCompileTimeBuiltinCall(irBuiltIns: IrBuiltIns): Boolean {
            val owner = this.symbol.owner

            if (!owner.fromStdlib()) return false

            val receiverType = owner.parameters.getOrNull(0)?.type?.toCompileTimeType()
            val firstArgType = owner.parameters.getOrNull(1)?.type?.toCompileTimeType()

            val inBuiltinMap = canEvalOp(
                callableId = owner.callableId ?: return false,
                typeA = receiverType,
                typeB = firstArgType
            )
            return inBuiltinMap
        }

        private fun IrDeclaration.fromStdlib(): Boolean {
            return this.getPackageFragment().packageFqName.startsWith(StandardNames.BUILT_INS_PACKAGE_NAME)
        }

        private fun IrConst.getCastedValue(): Any? {
            if (value == null) return null
            val constType = this.type.makeNotNull().removeAnnotations()
            return when (this.type.getPrimitiveType()) {
                PrimitiveType.BOOLEAN -> this.value as Boolean
                PrimitiveType.CHAR -> this.value as Char
                PrimitiveType.BYTE -> (this.value as Number).toByte()
                PrimitiveType.SHORT -> (this.value as Number).toShort()
                PrimitiveType.INT -> (this.value as Number).toInt()
                PrimitiveType.FLOAT -> (this.value as Number).toFloat()
                PrimitiveType.LONG -> (this.value as Number).toLong()
                PrimitiveType.DOUBLE -> (this.value as Number).toDouble()
                null -> when (constType.getUnsignedType()) {
                    UnsignedType.UBYTE -> if (this.value is UByte) this.value else (this.value as Number).toLong().toUByte()
                    UnsignedType.USHORT -> if (this.value is UShort) this.value else (this.value as Number).toLong().toUShort()
                    UnsignedType.UINT -> if (this.value is UInt) this.value else (this.value as Number).toLong().toUInt()
                    UnsignedType.ULONG -> if (this.value is ULong) this.value else (this.value as Number).toLong().toULong()
                    null -> when {
                        constType.isString() -> this.value as String
                        else -> error("Cannot convert IrConst ${this.render()} to ConstantValue")
                    }
                }
            }
        }
    }
}
