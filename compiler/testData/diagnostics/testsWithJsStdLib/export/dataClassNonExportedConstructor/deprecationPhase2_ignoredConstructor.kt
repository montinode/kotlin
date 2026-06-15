// RUN_PIPELINE_TILL: FRONTEND
// OPT_IN: kotlin.js.ExperimentalJsExport
// LANGUAGE: +ErrorAboutDataClassCopyVisibilityChange, -DataClassCopyRespectsConstructorVisibility
@JsExport
data class Data1 <!DATA_CLASS_COPY_JS_EXPORTABILITY_WILL_BE_CHANGED_ERROR!>@JsExport.Ignore constructor(val x: Int)<!> {
    fun member() {
        copy()
        this.copy()
    }

    companion object {
        fun of(): Data1 {
            return Data1(1).copy()
        }
    }
}

class NotExportedClass


@JsExport
data class Data2 <!DATA_CLASS_COPY_JS_EXPORTABILITY_WILL_BE_CHANGED_ERROR!>@JsExport.Ignore constructor(
    <!NON_EXPORTABLE_TYPE, NON_EXPORTABLE_TYPE_IN_SYNTHETIC_COPY_FUNCTION!>val x: NotExportedClass<!>
)<!> {
    fun member() {
        copy()
        this.copy()
    }

    companion object {
        fun of(): Data2 {
            return Data2(NotExportedClass()).copy()
        }
    }
}

@JsExport
data class Data3 <!DATA_CLASS_COPY_JS_EXPORTABILITY_WILL_BE_CHANGED_ERROR!>@JsExport.Ignore constructor(
    <!NON_EXPORTABLE_TYPE_IN_SYNTHETIC_COPY_FUNCTION!>@JsExport.Ignore val x: NotExportedClass<!>
)<!> {
    fun member() {
        copy()
        this.copy()
    }

    companion object {
        fun of(): Data3 {
            return Data3(NotExportedClass()).copy()
        }
    }
}
