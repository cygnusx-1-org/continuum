package ml.docilealligator.infinityforreddit.shadowbox

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.text.Spanned
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.graphics.Insets
import androidx.core.view.updatePadding
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.core.MarkwonTheme
import ml.docilealligator.infinityforreddit.activities.LinkResolverActivity
import ml.docilealligator.infinityforreddit.customviews.LinearLayoutManagerBugFixed
import ml.docilealligator.infinityforreddit.databinding.ShadowboxMediaTextBinding
import ml.docilealligator.infinityforreddit.markdown.MarkdownUtils

/**
 * A text post: the title over its selftext, rendered white on black with the same Markwon recipe
 * FullMarkdownActivity uses. Inline images and videos come out as links here; the comments screen
 * is one tap away for the full rendering.
 */
class ShadowboxTextPageFragment : ShadowboxPageFragment() {

    private var _binding: ShadowboxMediaTextBinding? = null
    private val binding: ShadowboxMediaTextBinding
        get() = _binding!!
    private var panelHeight = 0
    private var insets = Insets.NONE

    /** Where the body starts: clear of the status bar, plus a margin. */
    private var topInsetPadding = 0

    override fun onCreateMediaView(inflater: LayoutInflater, container: ViewGroup) {
        val binding = ShadowboxMediaTextBinding.inflate(inflater, container, true)
        _binding = binding
        host.titleTypeface?.let { binding.titleTextViewShadowboxMediaText.typeface = it }
        // The info panel already shows the title. Repeating it above the selftext put the same
        // sentence on screen twice; it is kept only when there is no body to show, where the
        // page would otherwise be blank.
        if (!hasBody()) {
            binding.titleTextViewShadowboxMediaText.text = post.title
            binding.titleTextViewShadowboxMediaText.visibility = View.VISIBLE
            binding.contentRecyclerViewShadowboxMediaText.visibility = View.GONE
        } else {
            binding.titleTextViewShadowboxMediaText.visibility = View.GONE
            binding.contentRecyclerViewShadowboxMediaText.visibility = View.VISIBLE
        }
        binding.contentRecyclerViewShadowboxMediaText.layoutManager = LinearLayoutManagerBugFixed(host)
        binding.root.setOnClickListener { toggleChrome() }
        addTapToToggleChrome(binding.contentRecyclerViewShadowboxMediaText)
    }

    /**
     * Whether the post has a body worth rendering. A selftext that only repeats the title is not
     * one: the panel and the title already say it, and a page of the same sentence twice over
     * is worse than the title alone.
     */
    private fun hasBody(): Boolean {
        val selfText = post.selfText
        if (selfText.isNullOrBlank()) {
            return false
        }
        val plain = post.selfTextPlainTrimmed ?: post.selfTextPlain ?: selfText
        return !plain.trim().equals(post.title.trim(), ignoreCase = true)
    }

    override fun loadMedia() {
        val selfText = post.selfText
        if (!hasBody() || selfText == null) {
            return
        }
        val markdownColor = Color.WHITE
        val isNsfw = post.isNSFW
        val miscPlugin = object : AbstractMarkwonPlugin() {
            override fun beforeSetText(textView: TextView, markdown: Spanned) {
                host.contentTypeface?.let { textView.setTypeface(it) }
                textView.setTextColor(markdownColor)
            }

            override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                builder.linkResolver { _, link ->
                    val intent = Intent(host, LinkResolverActivity::class.java)
                    intent.data = Uri.parse(link)
                    intent.putExtra(LinkResolverActivity.EXTRA_IS_NSFW, isNsfw)
                    host.startActivity(intent)
                }
            }

            override fun configureTheme(builder: MarkwonTheme.Builder) {
                builder.linkColor(customThemeWrapper.linkColor)
            }
        }
        val markwon = MarkdownUtils.createContentPreviewRedditMarkwon(
            host, miscPlugin, markdownColor, customThemeWrapper.spoilerBackgroundColor
        )
        val adapter = MarkdownUtils.createCustomTablesAdapter(host)
        binding.contentRecyclerViewShadowboxMediaText.adapter = adapter
        adapter.setMarkdown(markwon, selfText)
        @Suppress("NotifyDataSetChanged")
        adapter.notifyDataSetChanged()
    }

    override fun openFullViewer() {
        openComments()
    }

    override fun onInsetsChanged(insets: Insets) {
        this.insets = insets
        applyPadding()
    }

    override fun onPanelHeightChanged(height: Int) {
        panelHeight = height
        applyPadding()
    }

    private fun applyPadding() {
        val binding = _binding ?: return
        binding.root.updatePadding(left = insets.left, right = insets.right)
        topInsetPadding = insets.top + (16 * resources.displayMetrics.density).toInt()
        binding.contentRecyclerViewShadowboxMediaText.updatePadding(
            top = topInsetPadding, bottom = maxOf(panelHeight, insets.bottom)
        )
        // The title stands in for the body on a post that has none, so it starts where the body
        // would and has to clear the status bar by itself.
        binding.titleTextViewShadowboxMediaText.updatePadding(top = topInsetPadding)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        fun newInstance(position: Int, blur: Boolean): ShadowboxTextPageFragment {
            val fragment = ShadowboxTextPageFragment()
            fragment.arguments = baseArguments(position, blur)
            return fragment
        }
    }
}
