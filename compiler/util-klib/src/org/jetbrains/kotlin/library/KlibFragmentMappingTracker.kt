/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.library

import org.jetbrains.kotlin.konan.file.File

/**
 * Receives the mapping between a serialized metadata fragment written to a klib and the source file it originates from.
 */
interface KlibFragmentMappingTracker {
    /**
     * Reports that the serialized metadata fragment [outputFile] was produced from [sourceFile].
     * [sourceFile] is `null` when the fragment has no originating source file.
     */
    fun recordSourceFileToKlibFragmentMapping(sourceFile: File?, outputFile: File)
}
