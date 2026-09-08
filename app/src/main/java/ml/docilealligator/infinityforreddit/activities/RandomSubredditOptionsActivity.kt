package ml.docilealligator.infinityforreddit.activities

import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.TextViewCompat
import javax.inject.Inject
import javax.inject.Named
import ml.docilealligator.infinityforreddit.Infinity
import ml.docilealligator.infinityforreddit.R
import ml.docilealligator.infinityforreddit.account.AccountScope
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper
import ml.docilealligator.infinityforreddit.databinding.ActivityRandomSubredditOptionsBinding
import ml.docilealligator.infinityforreddit.events.RandomSubredditOpenedEvent
import ml.docilealligator.infinityforreddit.randomsubreddit.RandomSubredditNames
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils
import ml.docilealligator.infinityforreddit.utils.Utils
import org.greenrobot.eventbus.EventBus

/**
 * The three ways to be handed a subreddit nobody picked, on a screen of their own.
 *
 * They used to sit on the search screen itself, above the recent searches, where they pushed the
 * thing the user came to type at down the page and vanished again the moment autocomplete had
 * anything to say. Here they keep one place, one tap behind the dice in the search toolbar.
 *
 * Opening a pick closes the search screen behind along with this one, which is what happened when
 * these rows lived there. See [RandomSubredditOpenedEvent] for why that travels as an event.
 */
class RandomSubredditOptionsActivity : BaseActivity() {

    @Inject
    @field:Named("default")
    lateinit var mSharedPreferences: SharedPreferences

    @Inject
    @field:Named("current_account")
    lateinit var mCurrentAccountSharedPreferences: SharedPreferences

    @Inject
    @field:Named("nsfw_and_spoiler")
    lateinit var mNsfwAndSpoilerSharedPreferences: SharedPreferences

    @Inject
    lateinit var mCustomThemeWrapper: CustomThemeWrapper

    private lateinit var binding: ActivityRandomSubredditOptionsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        (application as Infinity).appComponent.inject(this)

        super.onCreate(savedInstanceState)

        binding = ActivityRandomSubredditOptionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyCustomTheme()

        attachSliderPanelIfApplicable()

        setUpInsets()

        setSupportActionBar(binding.toolbarRandomSubredditOptionsActivity)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        setTitle(R.string.random_subreddits)

        // Naming a NSFW subreddit picker is the kind of thing that makes the app awkward to open in
        // public, so the row is not offered to anyone who has turned NSFW off. The note goes with
        // it: it is about NSFW too, and it is the longest piece of text on the screen.
        val nsfw = mNsfwAndSpoilerSharedPreferences.getBoolean(
            AccountScope.key(accountName, SharedPreferencesUtils.NSFW_BASE), false
        )
        if (!nsfw) {
            binding.randomNsfwSubredditTextViewRandomSubredditOptionsActivity.visibility = View.GONE
            binding.noteTextViewRandomSubredditOptionsActivity.visibility = View.GONE
        }

        binding.randomSubredditTextViewRandomSubredditOptionsActivity.setOnClickListener {
            openRandomSubreddit(RandomSubredditNames.RANDOM)
        }
        binding.randomSubscribedSubredditTextViewRandomSubredditOptionsActivity.setOnClickListener {
            openRandomSubreddit(RandomSubredditNames.MYRANDOM)
        }
        binding.randomNsfwSubredditTextViewRandomSubredditOptionsActivity.setOnClickListener {
            openRandomSubreddit(RandomSubredditNames.RANDNSFW)
        }
    }

    private fun setUpInsets() {
        if (!isImmersiveInterfaceRespectForcedEdgeToEdge()) {
            return
        }
        // Android 15 draws every window edge to edge whatever it says; below that the window has to
        // be asked, and skipping it leaves the content already clear of the system bars and the
        // insets applied below pushing the toolbar down a second status bar's worth.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        } else {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            )
        }
        if (isChangeStatusBarIconColor()) {
            addOnOffsetChangedListener(binding.appbarLayoutRandomSubredditOptionsActivity)
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val windowInsets = Utils.getInsets(insets, false, isForcedImmersiveInterface())

            setMargins(
                binding.toolbarRandomSubredditOptionsActivity,
                windowInsets.left, windowInsets.top, windowInsets.right, IGNORE_MARGIN
            )
            binding.nestedScrollViewRandomSubredditOptionsActivity.setPadding(
                windowInsets.left, 0, windowInsets.right, windowInsets.bottom
            )

            WindowInsetsCompat.CONSUMED
        }
    }

    private fun openRandomSubreddit(randomSubredditName: String) {
        EventBus.getDefault().post(RandomSubredditOpenedEvent())
        startActivity(
            requireNotNull(FetchRandomSubredditActivity.intentFor(this, randomSubredditName))
        )
        finish()
    }

    override fun getDefaultSharedPreferences(): SharedPreferences = mSharedPreferences

    override fun getCurrentAccountSharedPreferences(): SharedPreferences =
        mCurrentAccountSharedPreferences

    override fun getCustomThemeWrapper(): CustomThemeWrapper = mCustomThemeWrapper

    override fun applyCustomTheme() {
        binding.root.setBackgroundColor(mCustomThemeWrapper.backgroundColor)
        applyAppBarLayoutAndCollapsingToolbarLayoutAndToolbarTheme(
            binding.appbarLayoutRandomSubredditOptionsActivity,
            binding.collapsingToolbarLayoutRandomSubredditOptionsActivity,
            binding.toolbarRandomSubredditOptionsActivity
        )
        val primaryTextColor = mCustomThemeWrapper.primaryTextColor
        val primaryIconTint = ColorStateList.valueOf(mCustomThemeWrapper.primaryIconColor)
        for (row in listOf(
            binding.randomSubredditTextViewRandomSubredditOptionsActivity,
            binding.randomSubscribedSubredditTextViewRandomSubredditOptionsActivity,
            binding.randomNsfwSubredditTextViewRandomSubredditOptionsActivity
        )) {
            row.setTextColor(primaryTextColor)
            TextViewCompat.setCompoundDrawableTintList(row, primaryIconTint)
        }
        binding.noteTextViewRandomSubredditOptionsActivity.setTextColor(
            mCustomThemeWrapper.secondaryTextColor
        )
        applyAppBarScrollFlagsIfApplicable(
            binding.collapsingToolbarLayoutRandomSubredditOptionsActivity
        )
        typeface?.let { Utils.setFontToAllTextViews(binding.root, it) }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return false
    }
}
