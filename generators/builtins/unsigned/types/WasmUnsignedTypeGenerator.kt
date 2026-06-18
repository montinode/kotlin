/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.generators.builtins.unsigned

import org.jetbrains.kotlin.generators.builtins.PrimitiveType
import org.jetbrains.kotlin.generators.builtins.UnsignedType
import org.jetbrains.kotlin.generators.builtins.numbers.primitives.ExpectActualModifier
import org.jetbrains.kotlin.generators.builtins.numbers.primitives.MethodBuilder
import unsigned.types.BaseUnsignedTypeGenerator
import java.io.PrintWriter

class WasmUnsignedTypeGenerator(type: UnsignedType, out: PrintWriter) : BaseUnsignedTypeGenerator(type, out, ExpectActualModifier.Actual) {
    private val intrinsicOperators = setOf("rem", "div")
    override val extraImports = setOf("kotlin.wasm.internal.*")

    override fun compareToBody(otherType: UnsignedType): String = when (type) {
        otherType if otherType == UnsignedType.UINT -> "wasm_u32_compareTo(this.data, other.data)"
        otherType if otherType == UnsignedType.ULONG -> "wasm_u64_compareTo(this.data, other.data)"
        else -> super.compareToBody(otherType)
    }

    override fun binaryOperatorsBody(operator: String, otherType: UnsignedType, returnType: UnsignedType): String {
        return if (type == otherType && type == returnType && operator in intrinsicOperators) {
            "implementedAsIntrinsic"
        } else super.binaryOperatorsBody(operator, otherType, returnType)
    }

    override fun floatingConversionBody(otherType: PrimitiveType): String = when (type) {
        UnsignedType.UINT, UnsignedType.ULONG -> "implementedAsIntrinsic"
        else -> super.floatingConversionBody(otherType)
    }

    override fun fromFloatingPointBody(otherType: PrimitiveType): String = when (type) {
        UnsignedType.UINT, UnsignedType.ULONG -> "implementedAsIntrinsic"
        else -> super.fromFloatingPointBody(otherType)
    }

    override fun signedConversionBody(otherType: UnsignedType): String = when (type) {
        UnsignedType.UINT if otherType == UnsignedType.ULONG -> "implementedAsIntrinsic"
        else -> super.signedConversionBody(otherType)
    }

    override fun unsignedConversionBody(otherType: UnsignedType): String = when (type) {
        UnsignedType.UINT if otherType == UnsignedType.ULONG -> "implementedAsIntrinsic"
        else -> super.unsignedConversionBody(otherType)
    }

    override fun toStringHashCodeBody(): String = when (type) {
        UnsignedType.UINT, UnsignedType.ULONG -> " utoa${type.bitSize}(this)"
        else -> super.toStringHashCodeBody()
    }

    private fun MethodBuilder.intrinsifyWith(operation: String) {
        annotations.add("WasmOp(WasmOp.$operation)")
        annotations.remove(INLINE_ONLY)
        modifySignature { isInline = false }
    }

    override fun MethodBuilder.patchMethodDeclaration() {
        when (methodName) {
            "rem" if type.capitalized.let { it == returnType && it == parameterType } -> when (type) {
                UnsignedType.UINT -> intrinsifyWith("I32_REM_U")
                UnsignedType.ULONG -> intrinsifyWith("I64_REM_U")
                else -> {}
            }
            "div" if type.capitalized.let { it == returnType && it == parameterType } -> when (type) {
                UnsignedType.UINT -> intrinsifyWith("I32_DIV_U")
                UnsignedType.ULONG -> intrinsifyWith("I64_DIV_U")
                else -> {}
            }
            "toFloat" -> when (type) {
                UnsignedType.UINT -> intrinsifyWith("F32_CONVERT_I32_U")
                UnsignedType.ULONG -> intrinsifyWith("F32_CONVERT_I64_U")
                else -> {}
            }
            "toDouble" -> when (type) {
                UnsignedType.UINT -> intrinsifyWith("F64_CONVERT_I32_U")
                UnsignedType.ULONG -> intrinsifyWith("F64_CONVERT_I64_U")
                else -> {}
            }
            "toUInt" -> when (extensionReceiver) {
                PrimitiveType.FLOAT.capitalized -> intrinsifyWith("I32_TRUNC_SAT_F32_U")
                PrimitiveType.DOUBLE.capitalized -> intrinsifyWith("I32_TRUNC_SAT_F64_U")
                else -> {}
            }
            "toULong" -> when (extensionReceiver) {
                PrimitiveType.FLOAT.capitalized -> intrinsifyWith("I64_TRUNC_SAT_F32_U")
                PrimitiveType.DOUBLE.capitalized -> intrinsifyWith("I64_TRUNC_SAT_F64_U")
                null if type == UnsignedType.UINT -> intrinsifyWith("I64_EXTEND_I32_U")
                else -> {}
            }
            "toLong" if type == UnsignedType.UINT -> intrinsifyWith("I64_EXTEND_I32_U")
            "compareTo" -> {
                annotations.remove(OVERRIDE_BY_INLINE)
                annotations.remove(INLINE_ONLY)
                modifySignature { isInline = false }
            }
        }
    }

}
