package ml.docilealligator.infinityforreddit.activities

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import javax.inject.Inject
import javax.inject.Named
import ml.docilealligator.infinityforreddit.Infinity
import ml.docilealligator.infinityforreddit.R
import ml.docilealligator.infinityforreddit.account.Account
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper
import ml.docilealligator.infinityforreddit.databinding.ActivityFetchRandomSubredditOrPostBinding
import ml.docilealligator.infinityforreddit.randomsubreddit.RandomSubredditNames
import ml.docilealligator.infinityforreddit.randomsubreddit.RandomSubredditRepository
import ml.docilealligator.infinityforreddit.utils.APIUtils
import retrofit2.Retrofit

/**
 * Holds the screen while a random subreddit is chosen, then hands off to
 * [ViewSubredditDetailActivity] and gets out of the way.
 *
 * Every pick costs one small request -- the check that the chosen subreddit has any posts -- so the
 * animation is nearly always brief. It is there for the times it is not: the first pick after
 * install, or the first after a name list has been replaced, which additionally confirm a batch of
 * a hundred names, and a cold start with no list on disk, which downloads one first.
 *
 * All three flavours share this screen rather than earning separate activities that would each
 * flash by just as fast.
 */
class FetchRandomSubredditActivity : BaseActivity() {

    companion object {
        private const val EXTRA_RANDOM_SUBREDDIT_NAME = "ERSN"

        /**
         * An Intent that opens a random subreddit of the flavour [subredditName] names, or null if
         * it names a real subreddit and should be opened normally.
         *
         * The one way to launch this screen, so the extra stays private and the flavour travels as
         * the name the caller already had rather than as booleans it has to decode.
         */
        @JvmStatic
        fun intentFor(context: Context, subredditName: String?): Intent? {
            val canonical = RandomSubredditNames.canonicalise(subredditName) ?: return null
            return Intent(context, FetchRandomSubredditActivity::class.java)
                .putExtra(EXTRA_RANDOM_SUBREDDIT_NAME, canonical)
        }
    }

    @Inject
    @Named("oauth")
    lateinit var mOauthRetrofit: Retrofit

    @Inject
    @Named("no_oauth")
    lateinit var mRetrofit: Retrofit

    @Inject
    @Named("default")
    lateinit var mSharedPreferences: SharedPreferences

    @Inject
    @Named("current_account")
    lateinit var mCurrentAccountSharedPreferences: SharedPreferences

    @Inject
    lateinit var mCustomThemeWrapper: CustomThemeWrapper

    @Inject
    lateinit var mRandomSubredditRepository: RandomSubredditRepository

    private lateinit var binding: ActivityFetchRandomSubredditOrPostBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        (application as Infinity).appComponent.inject(this)
        super.onCreate(savedInstanceState)

        binding = ActivityFetchRandomSubredditOrPostBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyCustomTheme()

        val listener = object : RandomSubredditRepository.PickListener {
            override fun onRandomSubredditPicked(subredditName: String) {
                // Backing out while the pick was in flight is a cancellation, not a destination.
                if (isFinishing || isDestroyed) {
                    return
                }
                startActivity(
                    Intent(this@FetchRandomSubredditActivity, ViewSubredditDetailActivity::class.java)
                        .putExtra(ViewSubredditDetailActivity.EXTRA_SUBREDDIT_NAME_KEY, subredditName)
                )
                finish()
            }

            override fun onRandomSubredditPickFailed() {
                if (isFinishing || isDestroyed) {
                    return
                }
                // Also the no-subscriptions outcome, which is a healthy device with nothing to
                // pick from rather than anything having gone wrong.
                Toast.makeText(
                    this@FetchRandomSubredditActivity,
                    R.string.fetch_random_thing_failed,
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
        val handler = Handler(Looper.getMainLooper())

        val anonymous = accountName == Account.ANONYMOUS_ACCOUNT
        mRandomSubredditRepository.pickForName(
            intent.getStringExtra(EXTRA_RANDOM_SUBREDDIT_NAME),
            accountName,
            if (anonymous) mRetrofit else mOauthRetrofit,
            if (anonymous) emptyMap() else APIUtils.getOAuthHeader(accessToken),
            handler,
            listener
        )
    }

    override fun getDefaultSharedPreferences(): SharedPreferences = mSharedPreferences

    override fun getCurrentAccountSharedPreferences(): SharedPreferences = mCurrentAccountSharedPreferences

    override fun getCustomThemeWrapper(): CustomThemeWrapper = mCustomThemeWrapper

    override fun applyCustomTheme() {
        binding.root.setBackgroundColor(mCustomThemeWrapper.backgroundColor)
    }
}
