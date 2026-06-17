/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlinx.serialization.compiler.backend.ir

import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.ir.types.AbstractIrTypeSubstitutor
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.IrTypeArgument
import org.jetbrains.kotlin.ir.types.IrTypeProjection
import org.jetbrains.kotlin.ir.types.IrTypeSubstitutor
import org.jetbrains.kotlin.ir.types.impl.makeTypeProjection
import org.jetbrains.kotlin.ir.ObsoleteDescriptorBasedAPI
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrExpressionBody
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol
import org.jetbrains.kotlin.ir.symbols.IrValueSymbol
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.deepCopyWithoutPatchingParents
import org.jetbrains.kotlin.ir.util.getAllSubstitutedSupertypes
import org.jetbrains.kotlin.ir.util.primaryConstructor
import org.jetbrains.kotlin.ir.util.properties
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid

fun IrBuilderWithScope.getProperty(receiver: IrExpression, property: IrProperty, expectedPropertyType: IrType? = null): IrExpression {
    return if (property.getter != null)
        irGet(expectedPropertyType ?: property.getter!!.returnType, receiver, property.getter!!.symbol)
    else
        irGetField(receiver, property.backingField!!, expectedPropertyType ?: property.backingField!!.type)
}

/**
 * Builds a substitutor that re-expresses types written in terms of the serializable class'es (or its supertypes')
 * type parameters using the type arguments of [scopeType] — i.e. the type parameters that are actually in scope
 * inside the generated serializer member. Without it, generated IR for a generic `@Serializable` class would reference
 * e.g. `T of Foo` while only `T of Foo.$serializer` is visible in that scope, which the IR validator rejects (KT-69305).
 *
 * Returns an empty (identity) substitutor for non-generic classes, so non-generic serializers are unaffected.
 */
internal fun serializableTypeParameterSubstitutor(scopeType: IrType): AbstractIrTypeSubstitutor {
    val simpleScope = scopeType as? IrSimpleType ?: return AbstractIrTypeSubstitutor.Empty
    val klass = (simpleScope.classifier as? IrClassSymbol)?.owner ?: return AbstractIrTypeSubstitutor.Empty
    if (klass.typeParameters.isEmpty() || klass.typeParameters.size != simpleScope.arguments.size) {
        return AbstractIrTypeSubstitutor.Empty
    }
    // Maps the class'es own type parameters directly to the in-scope type arguments...
    val direct = IrTypeSubstitutor(klass.typeParameters.map { it.symbol }, simpleScope.arguments, allowEmptySubstitution = true)
    val substitution = HashMap<IrTypeParameterSymbol, IrTypeArgument>()
    klass.typeParameters.forEachIndexed { i, tp -> substitution[tp.symbol] = simpleScope.arguments[i] }
    // ...and the supertypes' type parameters (used by inherited serializable properties), re-expressed in scope.
    for (supertype in getAllSubstitutedSupertypes(klass)) {
        val supertypeClass = (supertype.classifier as? IrClassSymbol)?.owner ?: continue
        supertypeClass.typeParameters.forEachIndexed { i, tp ->
            val argument = supertype.arguments.getOrNull(i) as? IrTypeProjection ?: return@forEachIndexed
            substitution[tp.symbol] = makeTypeProjection(direct.substitute(argument.type), argument.variance)
        }
    }
    return IrTypeSubstitutor(substitution, allowEmptySubstitution = true)
}

/*
  Create a function that creates `get property value expressions` for given corresponded constructor's param
    (constructor_params) -> get_property_value_expression
 */
fun IrBuilderWithScope.createPropertyByParamReplacer(
    irClass: IrClass,
    serialProperties: List<IrSerializableProperty>,
    instance: IrValueParameter
): (ValueParameterDescriptor) -> IrExpression? {
    val substitutor = serializableTypeParameterSubstitutor(instance.symbol.owner.type)

    fun IrSerializableProperty.irGet(): IrExpression {
        val ownerType = instance.symbol.owner.type
        return getProperty(
            irGet(
                type = ownerType,
                variable = instance.symbol
            ), ir, substitutor.substitute(type)
        )
    }

    val serialPropertiesMap = serialProperties.associateBy { it.ir }

    val transientPropertiesSet =
        irClass.declarations.asSequence()
            .filterIsInstance<IrProperty>()
            .filter { it.isNonStaticWithField }
            .filter { !serialPropertiesMap.containsKey(it) }
            .toSet()

    return { vpd ->
        val propertyDescriptor = irClass.properties.find { it.name == vpd.name }
        if (propertyDescriptor != null) {
            val value = serialPropertiesMap[propertyDescriptor]
            value?.irGet() ?: run {
                if (propertyDescriptor in transientPropertiesSet)
                    getProperty(
                        irGet(instance),
                        propertyDescriptor,
                        (propertyDescriptor.getter?.returnType ?: propertyDescriptor.backingField?.type)
                            ?.let { substitutor.substitute(it) }
                    )
                else null
            }
        } else {
            null
        }
    }
}

/*
    Creates an initializer adapter function that can replace IR expressions of getting constructor parameter value by some other expression.
    Also adapter may replace IR expression of getting `this` value by another expression.
     */
@OptIn(ObsoleteDescriptorBasedAPI::class)
fun createInitializerAdapter(
    irClass: IrClass,
    paramGetReplacer: (ValueParameterDescriptor) -> IrExpression?,
    thisGetReplacer: Pair<IrValueSymbol, () -> IrExpression>? = null
): (IrExpressionBody) -> IrExpression {
    val constructorParameters = irClass.primaryConstructor?.parameters
        .orEmpty()
        .mapTo(hashSetOf()) { it.symbol }

    val initializerTransformer = object : IrElementTransformerVoid() {
        // try to replace `get some value` expression
        override fun visitGetValue(expression: IrGetValue): IrExpression {
            val symbol = expression.symbol
            if (thisGetReplacer != null && thisGetReplacer.first == symbol) {
                // replace `get this value` expression
                return thisGetReplacer.second()
            }

            val descriptor = symbol.descriptor
            if (descriptor is ValueParameterDescriptor && symbol in constructorParameters) {
                // replace `get parameter value` expression
                paramGetReplacer(descriptor)?.let { return it }
            }

            // otherwise leave expression as it is
            return super.visitGetValue(expression)
        }
    }
    val defaultsMap = extractDefaultValuesFromConstructor(irClass)
    return fun(initializer: IrExpressionBody): IrExpression {
        val rawExpression = initializer.expression
        val expression =
            if (rawExpression.isInitializePropertyFromParameter()) {
                // this is a primary constructor property, use corresponding default of value parameter
                defaultsMap.getValue((rawExpression as IrGetValue).symbol)!!
            } else {
                rawExpression
            }
        return expression.deepCopyWithoutPatchingParents().transform(initializerTransformer, null)
    }
}

private fun extractDefaultValuesFromConstructor(irClass: IrClass?): Map<IrValueSymbol, IrExpression?> {
    if (irClass == null) return emptyMap()
    val original = irClass.constructors.singleOrNull { it.isPrimary }
    // default arguments of original constructor
    val defaultsMap: Map<IrValueSymbol, IrExpression?> =
        original?.parameters?.associate { it.symbol to it.defaultValue?.expression } ?: emptyMap()
    return defaultsMap + extractDefaultValuesFromConstructor(irClass.getSuperClassNotAny())
}
