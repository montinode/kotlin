/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.wasm.lower

import org.jetbrains.kotlin.backend.common.DeclarationTransformer
import org.jetbrains.kotlin.backend.common.lower.coroutines.getOrCreateFunctionWithContinuationStub
import org.jetbrains.kotlin.backend.wasm.WasmBackendContext
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.util.OperatorNameConventions
import kotlin.collections.plus

// Similar to AddFunctionSupertypeToSuspendFunctionLowering in K/Native.
// Differs in return type transformation for stack switching coroutines compilation.
internal class WasmAddFunctionSupertypeToSuspendFunctionLowering(val context: WasmBackendContext) : DeclarationTransformer {
    override fun transformFlat(declaration: IrDeclaration): List<IrDeclaration>? {
        if (declaration !is IrClass) return null
        return listOf(addMissingSupertypes(declaration))
    }

    private fun IrSimpleFunction.getLowered() = if (isSuspend)
        if (context.wasmUseStackSwitching) {
            getOrCreateFunctionWithContinuationStub(context) { it.returnType }
        } else {
            getOrCreateFunctionWithContinuationStub(context)
        }
    else
        this

    private fun IrClass.getInvokeFunction() = simpleFunctions().single {
        it.name == OperatorNameConventions.INVOKE
    }.getLowered()

    private fun addOverride(clazz: IrClass, alreadyOverridden: IrType, toOverride: IrType) {
        val alreadyOverriddenFunction = alreadyOverridden.classOrNull!!.owner.getInvokeFunction()
        val functionToOverride = toOverride.classOrNull!!.owner.getInvokeFunction()
        val invokeFunction = clazz.simpleFunctions().single { it.overrides(alreadyOverriddenFunction) }
        if (invokeFunction.modality == Modality.ABSTRACT) return
        clazz.superTypes += toOverride
        invokeFunction.overriddenSymbols += functionToOverride.symbol
    }

    private fun IrType.transformReturnType() =
        if (!context.wasmUseStackSwitching) context.irBuiltIns.anyNType else this

    private fun addMissingSupertypes(clazz: IrClass): IrClass {
        val suspendFunctionSuperTypes = getAllSubstitutedSupertypes(clazz).filter {
            it.isSuspendFunction() || it.isKSuspendFunction()
        }.toSet()

        val continuationClassSymbol = context.symbols.continuationClass

        fun IrSimpleType.getClassAt(index: Int) = (this.arguments.getOrNull(index) as? IrTypeProjection)?.type?.classOrNull

        val functionWithContinuationSuperTypes = getAllSubstitutedSupertypes(clazz).filter {
            (it.isFunction() || it.isKFunction()) &&
                    it.getClassAt(it.arguments.size - 2) == continuationClassSymbol
        }.toSet()

        for (suspendFunctionType in suspendFunctionSuperTypes) {
            val functionClassTypeArguments = suspendFunctionType.arguments.mapIndexed { index, argument ->
                val type = (argument as IrTypeProjection).type
                if (index == suspendFunctionType.arguments.indices.last) {
                    continuationClassSymbol.typeWith(type)
                } else {
                    type
                }
            } + (suspendFunctionType.arguments.last() as IrTypeProjection).type.transformReturnType()

            val genericFunctionSuperType =
                if (suspendFunctionType.isSuspendFunction()) {
                    context.irBuiltIns.functionN(functionClassTypeArguments.size - 1)
                } else {
                    context.irBuiltIns.kFunctionN(functionClassTypeArguments.size - 1)
                }

            val functionType = genericFunctionSuperType.typeWith(functionClassTypeArguments)

            addOverride(clazz, suspendFunctionType, functionType)
        }

        for (functionType in functionWithContinuationSuperTypes) {
            val suspendFunctionClassTypeArguments = functionType.arguments.dropLast(1).mapIndexed { index, argument ->
                val type = (argument as IrTypeProjection).type
                if (index == functionType.arguments.indices.last - 1) {
                    require(type.classOrNull == continuationClassSymbol)
                    when (val typeArgument = (type as IrSimpleType).arguments.single()) {
                        is IrTypeProjection -> typeArgument.type
                        is IrStarProjection -> context.irBuiltIns.anyNType
                    }
                } else {
                    type
                }
            }

            val genericSuspendFunctionType =
                if (functionType.isFunction()) {
                    context.irBuiltIns.suspendFunctionN(suspendFunctionClassTypeArguments.size - 1)
                } else {
                    context.irBuiltIns.kSuspendFunctionN(suspendFunctionClassTypeArguments.size - 1)
                }

            val suspendFunctionType = genericSuspendFunctionType.typeWith(suspendFunctionClassTypeArguments)
            addOverride(clazz, functionType, suspendFunctionType)
        }
        return clazz
    }
}
