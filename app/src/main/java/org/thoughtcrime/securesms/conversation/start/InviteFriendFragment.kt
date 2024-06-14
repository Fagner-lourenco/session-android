package org.thoughtcrime.securesms.conversation.start

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement.Absolute.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.primarySurface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import dagger.hilt.android.AndroidEntryPoint
import network.loki.messenger.R
import org.session.libsession.utilities.TextSecurePreferences
import org.thoughtcrime.securesms.preferences.copyPublicKey
import org.thoughtcrime.securesms.preferences.sendInvitationToUseSession
import org.thoughtcrime.securesms.ui.LocalColors
import org.thoughtcrime.securesms.ui.LocalType
import org.thoughtcrime.securesms.ui.components.AppBar
import org.thoughtcrime.securesms.ui.components.OutlineButton
import org.thoughtcrime.securesms.ui.components.OutlineCopyButton
import org.thoughtcrime.securesms.ui.components.SmallButtons
import org.thoughtcrime.securesms.ui.contentDescription
import org.thoughtcrime.securesms.ui.onCreateView

@AndroidEntryPoint
class InviteFriendFragment : Fragment() {
    lateinit var delegate: NewConversationDelegate

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = onCreateView {
        InviteFriend(
            TextSecurePreferences.getLocalNumber(LocalContext.current)!!,
            onBack = { delegate.onDialogBackPressed() },
            onClose = { delegate.onDialogClosePressed() },
            copyPublicKey = requireContext()::copyPublicKey,
            sendInvitation = requireContext()::sendInvitationToUseSession,
        )
    }
}

@Preview
@Composable
private fun PreviewInviteFriend() {
    InviteFriend("050000000")
}

@Composable
private fun InviteFriend(
    accountId: String,
    onBack: () -> Unit = {},
    onClose: () -> Unit = {},
    copyPublicKey: () -> Unit = {},
    sendInvitation: () -> Unit = {},
) {
    Column(modifier = Modifier.background(MaterialTheme.colors.primarySurface)) {
        AppBar(stringResource(R.string.invite_a_friend), onBack = onBack, onClose = onClose)
        Column(
            modifier = Modifier.padding(horizontal = 24.dp),
            verticalArrangement = spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .border(
                        width = 1.dp,
                        color = LocalColors.current.textSecondary,
                        shape = RoundedCornerShape(size = 13.dp)
                    )
                    .fillMaxWidth()
                    .wrapContentHeight()
            ) {
                Text(
                    accountId,
                    modifier = Modifier
                        .contentDescription("Your account ID")
                        .align(Alignment.Center)
                        .padding(22.dp),
                    textAlign = TextAlign.Center,
                    style = LocalType.current.base
                )
            }

            Text(
                stringResource(R.string.invite_your_friend_to_chat_with_you_on_session_by_sharing_your_account_id_with_them),
                textAlign = TextAlign.Center,
                style = LocalType.current.small,
                color = LocalColors.current.textSecondary,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            SmallButtons {
                Row(horizontalArrangement = spacedBy(20.dp)) {
                    OutlineButton(
                        modifier = Modifier
                            .weight(1f)
                            .contentDescription("Share button"),
                        text = stringResource(R.string.share),
                        onClick = sendInvitation
                    )

                    OutlineCopyButton(
                        modifier = Modifier.weight(1f),
                        onClick = copyPublicKey
                    )
                }
            }
        }
    }
}
