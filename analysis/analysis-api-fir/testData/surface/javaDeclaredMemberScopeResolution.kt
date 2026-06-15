// FILE: main.kt
open class Base {
    open val baseProperty: String = "base"
    fun baseFunction(): String = privateBaseFunction()

    private fun privateBaseFunction(): String = "foo"
}

// FILE: Impl.java
public class Impl extends Base {
    public int implField = 1;

    public int implMethod() {
        return implField;
    }

    @Override
    public String getBaseProperty() {
        return "impl";
    }

    private String privateImplMethod() {
        return "hoge";
    }
}
