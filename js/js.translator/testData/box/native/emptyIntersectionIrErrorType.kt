// ISSUE: KT-86740
// DONT_TARGET_EXACT_BACKEND: WASM_JS
// WASM_MUTE_REASON: UNSUPPORTED_JS_INTEROP

sealed external interface EventHandler<in E : Event, out C : EventTarget, out T : EventTarget>

inline fun <E : Event> eventHandler(
    noinline handler: (E) -> Unit,
): EventHandler<E, Nothing, Nothing> = handler.unsafeCast<EventHandler<E, Nothing, Nothing>>()

external interface EventTarget
external class Node : EventTarget

open external class Event
open external class MouseEvent : Event
open external class PointerEvent : MouseEvent
open external class WrongMouseEvent

external interface GlobalEventHandlers : EventTarget {
    var onclick: EventHandler<PointerEvent, GlobalEventHandlers, Node>?
        get() = definedExternally
        set(value) = definedExternally
}

fun test(event: WrongMouseEvent) {}

fun box(): String {
    val element = js("({})").unsafeCast<GlobalEventHandlers>()
    // `eventHandler(::test)` infers E into the empty intersection PointerEvent & WrongMouseEvent
    element.onclick = <!OTHER_ERROR_WITH_REASON!>eventHandler<!>(::test)
    return "OK"
}
