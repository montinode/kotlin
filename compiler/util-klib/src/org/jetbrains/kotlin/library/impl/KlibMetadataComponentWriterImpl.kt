/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.library.impl

import org.jetbrains.kotlin.incremental.components.ICFileMappingTracker
import org.jetbrains.kotlin.library.SerializedMetadata
import org.jetbrains.kotlin.library.components.KlibMetadataComponentLayout
import org.jetbrains.kotlin.library.writer.KlibComponentWriter
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.nameWithoutExtension
import org.jetbrains.kotlin.konan.file.File as KlibFile

/**
 * An implementation of [KlibComponentWriter] that writes [SerializedMetadata] to the constructed Klib library.
 */
internal class KlibMetadataComponentWriterImpl(
    private val metadata: SerializedMetadata,
    private val fileMappingTracker: ICFileMappingTracker? = null,
) : KlibComponentWriter {
    override fun writeTo(root: KlibFile) {
        val layout = KlibMetadataComponentLayout(root)
        layout.metadataDir.mkdirs()

        layout.moduleHeaderFile.writeBytes(metadata.module)

        metadata.fragmentNames.forEachIndexed { index, packageFqName ->
            val packageFragmentDir: KlibFile = layout.getPackageFragmentsDir(packageFqName)
            packageFragmentDir.mkdirs()

            val shortPackageName: String = packageFqName.substringAfterLast(".")
            val packageFragmentsWithSourceFilesParts = metadata.fragmentsWithSourceFiles[index]

            val padding: Int = packageFragmentsWithSourceFilesParts.size.toString().length
            fun withPadding(packageFragmentPartIndex: Int) = String.format("%0${padding}d", packageFragmentPartIndex)

            packageFragmentsWithSourceFilesParts.forEachIndexed { packageFragmentPartIndex, packageFragmentWithSourceFiles ->
                val (packageFragmentPart, sourceFilePart) = packageFragmentWithSourceFiles
                val sourceFileName = sourceFilePart?.let { Path(it) }?.nameWithoutExtension
                val packageFragmentFile = layout.getPackageFragmentFile(
                    packageFqName = packageFqName,
                    partName = sourceFileName ?: "${withPadding(packageFragmentPartIndex)}_$shortPackageName"
                )

                packageFragmentFile.writeBytes(packageFragmentPart)

                if (sourceFilePart != null) {
                    fileMappingTracker?.recordSourceFilesToOutputFileMapping(listOf(File(sourceFilePart)), File(packageFragmentFile.path))
                }
            }
        }
    }
}
