package app.gamenative.ui.component.dialog

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.gamenative.PluviaApp

/**
 * The head unit only draws the car screen. A [Dialog] is a second window, so it never
 * appears there. On Android Auto the same content is drawn inside the current screen.
 */
@Composable
fun CarDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit,
) {
    if (!PluviaApp.isCarProjection) {
        Dialog(
            onDismissRequest = onDismissRequest,
            properties = properties,
            content = content,
        )
        return
    }
    BackHandler(onBack = onDismissRequest)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    ) {
        content()
    }
}
