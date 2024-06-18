package org.thoughtcrime.securesms.conversation.new

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import network.loki.messenger.R
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.conversation.newmessage.Callbacks
import org.thoughtcrime.securesms.conversation.newmessage.Event
import org.thoughtcrime.securesms.conversation.newmessage.NewMessageViewModel
import org.thoughtcrime.securesms.conversation.newmessage.State
import org.thoughtcrime.securesms.conversation.start.NewConversationDelegate
import org.thoughtcrime.securesms.conversation.v2.ConversationActivityV2
import org.thoughtcrime.securesms.dependencies.DatabaseComponent
import org.thoughtcrime.securesms.showOpenUrlDialog
import org.thoughtcrime.securesms.ui.color.Colors
import org.thoughtcrime.securesms.ui.LoadingArcOr
import org.thoughtcrime.securesms.ui.color.LocalColors
import org.thoughtcrime.securesms.ui.LocalDimensions
import org.thoughtcrime.securesms.ui.PreviewTheme
import org.thoughtcrime.securesms.ui.SessionColorsParameterProvider
import org.thoughtcrime.securesms.ui.components.AppBar
import org.thoughtcrime.securesms.ui.components.BorderlessButtonWithIcon
import org.thoughtcrime.securesms.ui.components.MaybeScanQrCode
import org.thoughtcrime.securesms.ui.components.OutlineButton
import org.thoughtcrime.securesms.ui.components.SessionOutlinedTextField
import org.thoughtcrime.securesms.ui.components.SessionTabRow
import org.thoughtcrime.securesms.ui.components.SlimOutlineButton
import org.thoughtcrime.securesms.ui.contentDescription
import org.thoughtcrime.securesms.ui.createThemedComposeView

class NewMessageFragment : Fragment() {

    val viewModel: NewMessageViewModel by viewModels()

    lateinit var delegate: NewConversationDelegate

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            viewModel.event.filterIsInstance<Event.Success>().collect {
                createPrivateChat(it.key)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = createThemedComposeView {
        val uiState by viewModel.state.collectAsState(State())
        NewMessage(
            uiState,
            viewModel.qrErrors,
            viewModel,
            onClose = { delegate.onDialogClosePressed() },
            onBack = { delegate.onDialogBackPressed() },
            onHelp = { requireContext().showOpenUrlDialog("https://sessionapp.zendesk.com/hc/en-us/articles/4439132747033-How-do-Session-ID-usernames-work") }
        )
    }

    private fun createPrivateChat(hexEncodedPublicKey: String) {
        val recipient = Recipient.from(requireContext(), Address.fromSerialized(hexEncodedPublicKey), false)
        Intent(requireContext(), ConversationActivityV2::class.java).apply {
            putExtra(ConversationActivityV2.ADDRESS, recipient.address)
            setDataAndType(requireActivity().intent.data, requireActivity().intent.type)
            putExtra(ConversationActivityV2.THREAD_ID, DatabaseComponent.get(requireContext()).threadDatabase().getThreadIdIfExistsFor(recipient))
        }.let(requireContext()::startActivity)
        delegate.onDialogClosePressed()
    }
}

@Preview
@Composable
private fun PreviewNewMessage(
    @PreviewParameter(SessionColorsParameterProvider::class) colors: Colors
) {
    PreviewTheme(colors) {
        NewMessage(State())
    }
}

private val TITLES = listOf(R.string.enter_account_id, R.string.qrScan)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NewMessage(
    state: State,
    errors: Flow<String> = emptyFlow(),
    callbacks: Callbacks = object: Callbacks {},
    onClose: () -> Unit = {},
    onBack: () -> Unit = {},
    onHelp: () -> Unit = {},
) {
    val pagerState = rememberPagerState { TITLES.size }

    Column(modifier = Modifier.background(LocalColors.current.backgroundSecondary)) {
        AppBar(stringResource(R.string.messageNew), onClose = onClose, onBack = onBack)
        SessionTabRow(pagerState, TITLES)
        HorizontalPager(pagerState) {
            when (TITLES[it]) {
                R.string.enter_account_id -> EnterAccountId(state, callbacks, onHelp)
                R.string.qrScan -> MaybeScanQrCode(errors, onScan = callbacks::onScanQrCode)
            }
        }
    }
}

@Composable
fun EnterAccountId(
    state: State,
    callbacks: Callbacks,
    onHelp: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .padding(horizontal = LocalDimensions.current.marginExtraExtraSmall, vertical = LocalDimensions.current.marginExtraSmall)
            .fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(LocalDimensions.current.marginExtraSmall)
    ) {
        SessionOutlinedTextField(
            text = state.newMessageIdOrOns,
            modifier = Modifier
                .padding(horizontal = LocalDimensions.current.marginSmall)
                .contentDescription("Session id input box"),
            placeholder = stringResource(R.string.accountIdOrOnsEnter),
            onChange = callbacks::onChange,
            onContinue = callbacks::onContinue,
            error = state.error?.string(),
        )

        BorderlessButtonWithIcon(
            text = stringResource(R.string.messageNewDescription),
            iconRes = R.drawable.ic_circle_question_mark,
            contentColor = LocalColors.current.textSecondary,
            modifier = Modifier
                .animateContentSize()
                .contentDescription(R.string.AccessibilityId_help_desk_link)
                .padding(horizontal = LocalDimensions.current.marginMedium)
                .fillMaxWidth(),
        ) { onHelp() }

        OutlineButton(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = LocalDimensions.current.marginLarge)
                .fillMaxWidth()
                .contentDescription(R.string.next),
            color = LocalColors.current.primary,
            enabled = state.isNextButtonEnabled,
            onClick = { callbacks.onContinue() }
        ) {
            LoadingArcOr(state.loading) {
                Text(stringResource(R.string.next))
            }
        }
    }
}
