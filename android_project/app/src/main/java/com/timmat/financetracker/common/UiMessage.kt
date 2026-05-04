package com.timmat.financetracker.common

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource

/**
 * Allows ViewModels to emit user-visible messages **without** holding an
 * Android `Context`. Two resolvers are provided:
 *
 *  * [resolve] — from a `@Composable` scope, uses [stringResource] so the
 *    current locale is respected at read time.
 *  * [resolve(Context)] — from a non-composable scope (e.g. a `LaunchedEffect`
 *    coroutine body), uses `Context.getString`.
 */
sealed class UiMessage {
    /** Raw string — for dynamic / third-party error messages (e.g. Firebase). */
    data class Raw(val text: String) : UiMessage()
    /** Localised string resource id with optional format arguments. */
    class Res(val id: Int, vararg val args: Any) : UiMessage()
}

@Composable
fun UiMessage.resolve(): String = when (this) {
    is UiMessage.Raw -> text
    is UiMessage.Res -> stringResource(id = id, formatArgs = args)
}

fun UiMessage.resolve(context: Context): String = when (this) {
    is UiMessage.Raw -> text
    is UiMessage.Res -> context.getString(id, *args)
}

/** Convenience for grabbing the current Context inside a Composable. */
@Composable
fun rememberAppContext(): Context = LocalContext.current
