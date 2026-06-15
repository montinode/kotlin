// RUN_PIPELINE_TILL: FRONTEND
// LATEST_LV_DIFFERENCE
// IGNORE_DEXING
// OPT_IN: kotlin.js.ExperimentalJsExport
// LANGUAGE: -ErrorAboutDataClassCopyVisibilityChange, -DataClassCopyRespectsConstructorVisibility
@file:JsExport

data class Data1 <!DATA_CLASS_COPY_JS_EXPORTABILITY_WILL_BE_CHANGED_WARNING!>@JsExport.Ignore constructor(val x: Int)<!>

@JsExport.Ignore
class NotExportedClass

data class Data2 <!DATA_CLASS_COPY_JS_EXPORTABILITY_WILL_BE_CHANGED_WARNING!>@JsExport.Ignore constructor(
    <!NON_EXPORTABLE_TYPE, NON_EXPORTABLE_TYPE_IN_SYNTHETIC_COPY_FUNCTION!>val x: NotExportedClass<!>
)<!>

data class Data3 <!DATA_CLASS_COPY_JS_EXPORTABILITY_WILL_BE_CHANGED_WARNING!>@JsExport.Ignore constructor(
    <!NON_EXPORTABLE_TYPE_IN_SYNTHETIC_COPY_FUNCTION!>@JsExport.Ignore val x: NotExportedClass<!>
)<!>
