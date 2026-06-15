// RUN_PIPELINE_TILL: BACKEND
// WITH_STDLIB
// OPT_IN: kotlin.js.ExperimentalJsExport
// LANGUAGE: +ErrorAboutDataClassCopyVisibilityChange, -DataClassCopyRespectsConstructorVisibility
@JsExport
@ExposedCopyVisibility
data class Data1 @JsExport.Ignore constructor(val x: Int)

class NotExportedClass

@JsExport
@ExposedCopyVisibility
data class Data2 @JsExport.Ignore constructor(
    <!NON_EXPORTABLE_TYPE, NON_EXPORTABLE_TYPE_IN_SYNTHETIC_COPY_FUNCTION!>val x: NotExportedClass<!>
)

@JsExport
@ExposedCopyVisibility
data class Data3 @JsExport.Ignore constructor(
    <!NON_EXPORTABLE_TYPE_IN_SYNTHETIC_COPY_FUNCTION!>@JsExport.Ignore val x: NotExportedClass<!>
)
