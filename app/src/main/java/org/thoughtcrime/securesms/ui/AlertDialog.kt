package org.thoughtcrime.securesms.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import network.loki.messenger.R
import org.thoughtcrime.securesms.ui.color.LocalColors

class DialogButtonModel(
    val text: GetString,
    val contentDescription: GetString = text,
    val onClick: () -> Unit
)

@Composable
fun AlertDialog(
    onDismissRequest: () -> Unit,
    title: String? = null,
    text: String? = null,
    buttons: List<DialogButtonModel>? = null
) {
    androidx.compose.material.AlertDialog(
        onDismissRequest,
        shape = MaterialTheme.shapes.small,
        backgroundColor = LocalColors.current.backgroundSecondary,
        buttons = {
            Box {
                IconButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_dialog_x),
                        tint = LocalColors.current.text,
                        contentDescription = "back"
                    )
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = LocalDimensions.current.itemSpacingSmall)
                            .padding(horizontal = LocalDimensions.current.itemSpacingSmall)
                    ) {
                        title?.let {
                            Text(
                                it,
                                textAlign = TextAlign.Center,
                                style = h7,
                                modifier = Modifier.padding(bottom = LocalDimensions.current.itemSpacingXXSmall)
                            )
                        }
                        text?.let {
                            Text(
                                it,
                                textAlign = TextAlign.Center,
                                style = large,
                                modifier = Modifier.padding(bottom = LocalDimensions.current.itemSpacingXXSmall)
                            )
                        }
                    }
                    buttons?.takeIf { it.isNotEmpty() }?.let {
                        Row {
                            it.forEach {
                                DialogButton(
                                    text = it.text(),
                                    modifier = Modifier
                                        .contentDescription(it.contentDescription())
                                        .weight(1f)
                                ) {
                                    it.onClick()
                                    onDismissRequest()
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun DialogButton(text: String, modifier: Modifier, onClick: () -> Unit) {
    TextButton(
        modifier = modifier,
        shape = RectangleShape,
        onClick = onClick
    ) {
        Text(
            text,
            color = LocalColors.current.text,
            style = largeBold,
            modifier = Modifier.padding(
                top = LocalDimensions.current.itemSpacingSmall,
                bottom = LocalDimensions.current.itemSpacingMedium
            )
        )
    }
}
