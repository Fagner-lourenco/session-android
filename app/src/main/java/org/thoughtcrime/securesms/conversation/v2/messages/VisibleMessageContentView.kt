package org.thoughtcrime.securesms.conversation.v2.messages

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.text.Spannable
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.URLSpan
import android.text.util.Linkify
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.graphics.ColorUtils
import androidx.core.text.getSpans
import androidx.core.text.toSpannable
import androidx.core.view.children
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestManager
import network.loki.messenger.R
import network.loki.messenger.databinding.ViewVisibleMessageContentBinding
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.session.libsession.messaging.sending_receiving.attachments.AttachmentTransferProgress
import org.session.libsession.messaging.sending_receiving.attachments.DatabaseAttachment
import org.session.libsession.utilities.ThemeUtil
import org.session.libsession.utilities.getColorFromAttr
import org.session.libsession.utilities.modifyLayoutParams
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.conversation.v2.ConversationActivityV2
import org.thoughtcrime.securesms.conversation.v2.utilities.MentionUtilities
import org.thoughtcrime.securesms.conversation.v2.utilities.ModalURLSpan
import org.thoughtcrime.securesms.conversation.v2.utilities.TextUtilities.getIntersectedModalSpans
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.mms.PartAuthority
import org.thoughtcrime.securesms.util.GlowViewUtilities
import org.thoughtcrime.securesms.util.SearchUtil
import org.thoughtcrime.securesms.util.getAccentColor
import java.util.Locale
import kotlin.math.roundToInt

class VisibleMessageContentView : ConstraintLayout {
    private val binding: ViewVisibleMessageContentBinding by lazy { ViewVisibleMessageContentBinding.bind(this) }
    var onContentDoubleTap: (() -> Unit)? = null
    var delegate: VisibleMessageViewDelegate? = null
    var indexInAdapter: Int = -1

    // region Lifecycle
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
    // endregion

    // region Updating
    fun bind(
        message: MessageRecord,
        isStartOfMessageCluster: Boolean = true,
        isEndOfMessageCluster: Boolean = true,
        glide: RequestManager = Glide.with(this),
        thread: Recipient,
        searchQuery: String? = null,
        onAttachmentNeedsDownload: (DatabaseAttachment) -> Unit,
        suppressThumbnails: Boolean = false
    ) {
        // Background
        val color = if (message.isOutgoing) context.getAccentColor()
        else context.getColorFromAttr(R.attr.message_received_background_color)
        binding.contentParent.mainColor = color
        binding.contentParent.cornerRadius = resources.getDimension(R.dimen.message_corner_radius)

        val mediaDownloaded = message is MmsMessageRecord && message.slideDeck.asAttachments().all { it.transferState == AttachmentTransferProgress.TRANSFER_PROGRESS_DONE }
        val mediaInProgress = message is MmsMessageRecord && message.slideDeck.asAttachments().any { it.isInProgress }
        val mediaThumbnailMessage = message is MmsMessageRecord && message.slideDeck.thumbnailSlide != null

        // reset visibilities / containers
        onContentClick.clear()
        binding.albumThumbnailView.root.clearViews()
        onContentDoubleTap = null

        if (message.isDeleted) {
            binding.contentParent.isVisible = true
            binding.deletedMessageView.root.isVisible = true
            binding.deletedMessageView.root.bind(message, getTextColor(context, message))
            binding.bodyTextView.isVisible = false
            binding.quoteView.root.isVisible = false
            binding.linkPreviewView.root.isVisible = false
            binding.voiceMessageView.root.isVisible = false
            binding.documentView.root.isVisible = false
            binding.albumThumbnailView.root.isVisible = false
            binding.openGroupInvitationView.root.isVisible = false
            return
        } else {
            binding.deletedMessageView.root.isVisible = false
        }

        // Note: Need to clear the body to prevent the message bubble getting incorrectly
        // sized based on text content from a recycled view
        binding.bodyTextView.text = null
        binding.quoteView.root.isVisible = message is MmsMessageRecord && message.quote != null
        binding.linkPreviewView.root.isVisible = message is MmsMessageRecord && message.linkPreviews.isNotEmpty()
        binding.pendingAttachmentView.root.isVisible = !mediaDownloaded && !mediaInProgress && message is MmsMessageRecord && message.quote == null && message.linkPreviews.isEmpty()
        binding.voiceMessageView.root.isVisible = (mediaDownloaded || mediaInProgress) && message is MmsMessageRecord && message.slideDeck.audioSlide != null
        binding.documentView.root.isVisible = (mediaDownloaded || mediaInProgress) && message is MmsMessageRecord && message.slideDeck.documentSlide != null
        binding.albumThumbnailView.root.isVisible = mediaThumbnailMessage
        binding.openGroupInvitationView.root.isVisible = message.isOpenGroupInvitation

        var hideBody = false

        if (message is MmsMessageRecord && message.quote != null) {
            binding.quoteView.root.isVisible = true
            val quote = message.quote!!
            val quoteText = if (quote.isOriginalMissing) {
                context.getString(R.string.messageErrorOriginal)
            } else {
                quote.text
            }
            binding.quoteView.root.bind(quote.author.toString(), quoteText, quote.attachment, thread,
                message.isOutgoing, message.isOpenGroupInvitation, message.threadId,
                quote.isOriginalMissing, glide)
            onContentClick.add { event ->
                val r = Rect()
                binding.quoteView.root.getGlobalVisibleRect(r)
                if (r.contains(event.rawX.roundToInt(), event.rawY.roundToInt())) {
                    delegate?.highlightMessageFromTimestamp(quote.id)
                }
            }
        }

        if (message is MmsMessageRecord) {
            message.slideDeck.asAttachments().forEach { attach ->
                val dbAttachment = attach as? DatabaseAttachment ?: return@forEach
                onAttachmentNeedsDownload(dbAttachment)
            }
            message.linkPreviews.forEach { preview ->
                val previewThumbnail = preview.getThumbnail().orNull() as? DatabaseAttachment ?: return@forEach
                onAttachmentNeedsDownload(previewThumbnail)
            }
        }

        when {
            // LINK PREVIEW
            message is MmsMessageRecord && message.linkPreviews.isNotEmpty() -> {
                binding.linkPreviewView.root.bind(message, glide, isStartOfMessageCluster, isEndOfMessageCluster)
                onContentClick.add { event -> binding.linkPreviewView.root.calculateHit(event) }

                // When in a link preview ensure the bodyTextView can expand to the full width
                binding.bodyTextView.maxWidth = binding.linkPreviewView.root.layoutParams.width
            }

            // AUDIO
            message is MmsMessageRecord && message.slideDeck.audioSlide != null -> {

                // Show any text message associated with the audio message (which may be a voice clip - but could also be a mp3 or such)
                hideBody = false

                // Audio attachment
                if (mediaDownloaded || mediaInProgress || message.isOutgoing) {
                    binding.voiceMessageView.root.indexInAdapter = indexInAdapter
                    binding.voiceMessageView.root.delegate = context as? ConversationActivityV2
                    binding.voiceMessageView.root.bind(message, isStartOfMessageCluster, isEndOfMessageCluster)
                    // We have to use onContentClick (rather than a click listener directly on the voice
                    // message view) so as to not interfere with all the other gestures.
                    onContentClick.add { binding.voiceMessageView.root.togglePlayback() }
                    onContentDoubleTap = { binding.voiceMessageView.root.handleDoubleTap() }
                } else {
                    // If it's an audio message but we haven't downloaded it yet show it as pending
                    (message.slideDeck.audioSlide?.asAttachment() as? DatabaseAttachment)?.let { attachment ->
                        binding.pendingAttachmentView.root.bind(
                            PendingAttachmentView.AttachmentType.AUDIO,
                            getTextColor(context,message),
                            attachment
                        )
                        onContentClick.add { binding.pendingAttachmentView.root.showDownloadDialog(thread, attachment) }
                    }
                }
            }

            // DOCUMENT
            message is MmsMessageRecord && message.slideDeck.documentSlide != null -> {
                // Show any message that came with the attached document
                hideBody = false
                
                // Document attachment
                if (mediaDownloaded || mediaInProgress || message.isOutgoing) {
                    binding.documentView.root.bind(message, getTextColor(context, message))
                    message.slideDeck.documentSlide?.let { slide ->
                        if(!mediaInProgress) { // do not attempt to open a doc in progress of downloading
                            onContentClick.add {
                                // open the document when tapping it
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW)
                                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    intent.setDataAndType(
                                        PartAuthority.getAttachmentPublicUri(slide.uri),
                                        slide.contentType
                                    )

                                    context.startActivity(intent)
                                } catch (e: ActivityNotFoundException) {
                                    Log.e("VisibleMessageContentView", "Error opening document", e)
                                    Toast.makeText(
                                        context,
                                        R.string.attachmentsErrorOpen,
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    }
                } else {
                    // If the document hasn't been downloaded yet then show it as pending
                    (message.slideDeck.documentSlide?.asAttachment() as? DatabaseAttachment)?.let { attachment ->
                        binding.pendingAttachmentView.root.bind(
                            PendingAttachmentView.AttachmentType.DOCUMENT,
                            getTextColor(context,message),
                            attachment
                            )
                        onContentClick.add {
                            binding.pendingAttachmentView.root.showDownloadDialog(thread, attachment)
                        }
                    }
                }
            }

            // IMAGE / VIDEO
            message is MmsMessageRecord && !suppressThumbnails && message.slideDeck.asAttachments().isNotEmpty() -> {
                if (mediaDownloaded || mediaInProgress || message.isOutgoing) {
                    // isStart and isEnd of cluster needed for calculating the mask for full bubble image groups
                    // bind after add view because views are inflated and calculated during bind
                    binding.albumThumbnailView.root.bind(
                        glideRequests = glide,
                        message = message,
                        isStart = isStartOfMessageCluster,
                        isEnd = isEndOfMessageCluster
                    )
                    binding.albumThumbnailView.root.modifyLayoutParams<LayoutParams> {
                        horizontalBias = if (message.isOutgoing) 1f else 0f
                    }
                    onContentClick.add { event ->
                        binding.albumThumbnailView.root.calculateHitObject(event, message, thread, onAttachmentNeedsDownload)
                    }
                } else {
                    hideBody = true
                    binding.albumThumbnailView.root.clearViews()
                    val firstAttachment = message.slideDeck.asAttachments().first() as? DatabaseAttachment
                    firstAttachment?.let { attachment ->
                        binding.pendingAttachmentView.root.bind(
                            PendingAttachmentView.AttachmentType.IMAGE,
                            getTextColor(context,message),
                            attachment
                            )
                        onContentClick.add {
                            binding.pendingAttachmentView.root.showDownloadDialog(thread, attachment)
                        }
                    }
                }
            }
            message.isOpenGroupInvitation -> {
                hideBody = true
                binding.openGroupInvitationView.root.bind(message, getTextColor(context, message))
                onContentClick.add { binding.openGroupInvitationView.root.joinOpenGroup() }
            }
        }

        binding.bodyTextView.isVisible = message.body.isNotEmpty() && !hideBody
        binding.contentParent.apply { isVisible = children.any { it.isVisible } }

        if (message.body.isNotEmpty() && !hideBody) {
            val color = getTextColor(context, message)
            binding.bodyTextView.setTextColor(color)
            binding.bodyTextView.setLinkTextColor(color)
            val body = getBodySpans(context, message, searchQuery)
            binding.bodyTextView.text = body
            onContentClick.add { e: MotionEvent ->
                binding.bodyTextView.getIntersectedModalSpans(e).iterator().forEach { span ->
                    span.onClick(binding.bodyTextView)
                }
            }
        }
        binding.contentParent.modifyLayoutParams<ConstraintLayout.LayoutParams> {
            horizontalBias = if (message.isOutgoing) 1f else 0f
        }
    }

    private val onContentClick: MutableList<((event: MotionEvent) -> Unit)> = mutableListOf()

    fun onContentClick(event: MotionEvent) {
        onContentClick.forEach { clickHandler -> clickHandler.invoke(event) }
    }

    private fun ViewVisibleMessageContentBinding.barrierViewsGone(): Boolean =
        listOf<View>(albumThumbnailView.root, linkPreviewView.root, voiceMessageView.root, quoteView.root).none { it.isVisible }

    fun recycle() {
        arrayOf(
            binding.deletedMessageView.root,
            binding.pendingAttachmentView.root,
            binding.voiceMessageView.root,
            binding.openGroupInvitationView.root,
            binding.documentView.root,
            binding.quoteView.root,
            binding.linkPreviewView.root,
            binding.albumThumbnailView.root,
            binding.bodyTextView
        ).forEach { view: View -> view.isVisible = false }
    }

    fun playVoiceMessage() {
        binding.voiceMessageView.root.togglePlayback()
    }

    fun playHighlight() {
        // Show the highlight colour immediately then slowly fade out
        val targetColor = if (ThemeUtil.isDarkTheme(context)) context.getAccentColor() else resources.getColor(R.color.black, context.theme)
        val clearTargetColor = ColorUtils.setAlphaComponent(targetColor, 0)
        binding.contentParent.numShadowRenders = if (ThemeUtil.isDarkTheme(context)) 3 else 1
        binding.contentParent.sessionShadowColor = targetColor
        GlowViewUtilities.animateShadowColorChange(binding.contentParent, targetColor, clearTargetColor, 1600)
    }
    // endregion

    // region Convenience
    companion object {

        fun getBodySpans(context: Context, message: MessageRecord, searchQuery: String?): Spannable {
            var body = message.body.toSpannable()

            body = MentionUtilities.highlightMentions(
                text = body,
                isOutgoingMessage = message.isOutgoing,
                threadID = message.threadId,
                context = context
            )
            body = SearchUtil.getHighlightedSpan(Locale.getDefault(),
                { BackgroundColorSpan(Color.WHITE) }, body, searchQuery)
            body = SearchUtil.getHighlightedSpan(Locale.getDefault(),
                { ForegroundColorSpan(Color.BLACK) }, body, searchQuery)

            Linkify.addLinks(body, Linkify.WEB_URLS)

            // replace URLSpans with ModalURLSpans
            body.getSpans<URLSpan>(0, body.length).toList().forEach { urlSpan ->
                val updatedUrl = urlSpan.url.let { it.toHttpUrlOrNull().toString() }
                val replacementSpan = ModalURLSpan(updatedUrl) { url ->
                    val activity = context as? ConversationActivityV2
                    activity?.showOpenUrlDialog(url)
                }
                val start = body.getSpanStart(urlSpan)
                val end = body.getSpanEnd(urlSpan)
                val flags = body.getSpanFlags(urlSpan)
                body.removeSpan(urlSpan)
                body.setSpan(replacementSpan, start, end, flags)
            }
            return body
        }

        @ColorInt
        fun getTextColor(context: Context, message: MessageRecord): Int = context.getColorFromAttr(
            if (message.isOutgoing) R.attr.message_sent_text_color else R.attr.message_received_text_color
        )
    }
    // endregion
}
