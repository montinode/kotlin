/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.library

class SerializedMetadata(
    val module: ByteArray,
    val fragmentNames: List<String>,
    val metadataVersion: IntArray,
    val fragmentsWithSourceFiles: List<List<Pair<ByteArray, String?>>> = emptyList(),
) {
    constructor(
        module: ByteArray,
        fragments: List<List<ByteArray>>,
        fragmentNames: List<String>,
        metadataVersion: IntArray,
    ) : this(module, fragmentNames, metadataVersion, fragments.map { fragment -> fragment.map { Pair(it, null) } })

    val fragments: List<List<ByteArray>> =
        fragmentsWithSourceFiles.map { fragmentsWithSourceFile -> fragmentsWithSourceFile.map { it.first } }
}

class SerializedDeclaration(val id: Int, val bytes: ByteArray) {
    val size = bytes.size
}

class SerializedIrFile(
    val fileData: ByteArray,
    val fqName: String,
    val path: String,
    val types: ByteArray,
    val signatures: ByteArray,
    val strings: ByteArray,
    val bodies: ByteArray,
    val declarations: ByteArray,
    val debugInfo: ByteArray?,
    val backendSpecificMetadata: ByteArray?,
    val fileEntries: ByteArray?,
)

class SerializedIrModule(
    val files: Collection<SerializedIrFile>,
    val fileWithPreparedInlinableFunctions: SerializedIrFile?,
)
