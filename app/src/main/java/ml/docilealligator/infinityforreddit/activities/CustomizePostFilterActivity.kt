package ml.docilealligator.infinityforreddit.activities

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.os.BundleCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import ml.docilealligator.infinityforreddit.Infinity
import ml.docilealligator.infinityforreddit.R
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase
import ml.docilealligator.infinityforreddit.adapters.PostFilterRulesRecyclerViewAdapter
import ml.docilealligator.infinityforreddit.bottomsheetfragments.AddPostFilterRuleBottomSheetFragment
import ml.docilealligator.infinityforreddit.bottomsheetfragments.NewPostFilterUsageBottomSheetFragment
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper
import ml.docilealligator.infinityforreddit.databinding.ActivityCustomizePostFilterBinding
import ml.docilealligator.infinityforreddit.postfilter.FilterRule
import ml.docilealligator.infinityforreddit.postfilter.PostFilter
import ml.docilealligator.infinityforreddit.postfilter.PostFilterRules
import ml.docilealligator.infinityforreddit.postfilter.PostFilterUsage
import ml.docilealligator.infinityforreddit.postfilter.RuleField
import ml.docilealligator.infinityforreddit.subreddit.SubredditWithSelection
import ml.docilealligator.infinityforreddit.utils.Utils
import ml.docilealligator.infinityforreddit.viewmodels.CustomizePostFilterViewModel
import ml.docilealligator.infinityforreddit.viewmodels.SavePostFilterResult
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Named

/**
 * Edits one post filter.
 *
 * The twelve include/exclude term columns of [PostFilter] are presented as a single list of
 * [FilterRule]s, each carrying its own polarity, with display-only chips to narrow the list down.
 * Nothing about the stored shape changed — [PostFilterRules] converts in both directions — so
 * [PostFilter.isPostAllowed], the backup format and the database schema are all untouched.
 *
 * The feeds this filter applies to are edited here too, rather than on a separate screen nobody
 * found. They are held in memory until the filter is written, because `post_filter_usage` has a
 * foreign key onto the filter row and so cannot be written before it exists.
 */
class CustomizePostFilterActivity :
    BaseActivity(),
    PostFilterRulesRecyclerViewAdapter.Callback,
    AddPostFilterRuleBottomSheetFragment.Host,
    NewPostFilterUsageBottomSheetFragment.Host {

    companion object {
        const val EXTRA_POST_FILTER = "EPF"
        const val EXTRA_FROM_SETTINGS = "EFS"
        const val EXTRA_EXCLUDE_SUBREDDIT = "EES"
        const val EXTRA_CONTAIN_SUBREDDIT = "ECS"
        const val EXTRA_EXCLUDE_USER = "EEU"
        const val EXTRA_CONTAIN_USER = "ECU"
        const val EXTRA_EXCLUDE_FLAIR = "EEF"
        const val EXTRA_CONTAIN_FLAIR = "ECF"
        const val EXTRA_EXCLUDE_DOMAIN = "EED"
        const val EXTRA_CONTAIN_DOMAIN = "ECD"
        const val EXTRA_START_FILTERED_POSTS_WHEN_FINISH = "ESFPWF"
        const val RETURN_EXTRA_POST_FILTER = "REPF"

        private const val POST_FILTER_STATE = "PFS"
        private const val ORIGINAL_NAME_STATE = "ONS"
        private const val RULES_STATE = "RS"
        private const val USAGES_STATE = "US"
        private const val PENDING_RULE_EXCLUDE_STATE = "PRES"

        private const val ADD_RULE_SUBREDDITS_REQUEST_CODE = 1
        private const val ADD_RULE_USERS_REQUEST_CODE = 3
        private const val ADD_USAGE_SUBREDDITS_REQUEST_CODE = 5
        private const val ADD_USAGE_USERS_REQUEST_CODE = 7
    }

    @Inject
    lateinit var mRedditDataRoomDatabase: RedditDataRoomDatabase

    @Inject
    @field:Named("default")
    lateinit var mSharedPreferences: SharedPreferences

    @Inject
    @field:Named("current_account")
    lateinit var mCurrentAccountSharedPreferences: SharedPreferences

    @Inject
    lateinit var mCustomThemeWrapper: CustomThemeWrapper

    @Inject
    lateinit var mExecutor: Executor

    private lateinit var binding: ActivityCustomizePostFilterBinding
    private lateinit var adapter: PostFilterRulesRecyclerViewAdapter
    private lateinit var customizePostFilterViewModel: CustomizePostFilterViewModel
    private lateinit var postFilter: PostFilter
    private lateinit var originalName: String
    private val rules = ArrayList<FilterRule>()
    private val usages = ArrayList<PostFilterUsage>()
    private var fromSettings = false

    /** The polarity the pending subreddit / user picker will apply to everything it returns. */
    private var pendingRuleExclude = true
    private var activeDialog: AlertDialog? = null
    private var usageDialogEditText: TextInputEditText? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        (application as Infinity).appComponent.inject(this)

        setImmersiveModeNotApplicableBelowAndroid16()

        super.onCreate(savedInstanceState)
        binding = ActivityCustomizePostFilterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyCustomTheme()

        customizePostFilterViewModel = ViewModelProvider(
            this,
            CustomizePostFilterViewModel.provideFactory(mExecutor, mRedditDataRoomDatabase)
        )[CustomizePostFilterViewModel::class.java]

        fromSettings = intent.getBooleanExtra(EXTRA_FROM_SETTINGS, false)

        if (savedInstanceState != null) {
            // onSaveInstanceState always writes every key.
            postFilter = requireNotNull(
                BundleCompat.getParcelable(savedInstanceState, POST_FILTER_STATE, PostFilter::class.java)
            )
            originalName = requireNotNull(savedInstanceState.getString(ORIGINAL_NAME_STATE))
            rules.addAll(
                BundleCompat.getParcelableArrayList(savedInstanceState, RULES_STATE, FilterRule::class.java)
                    ?: emptyList()
            )
            usages.addAll(
                BundleCompat.getParcelableArrayList(
                    savedInstanceState, USAGES_STATE, PostFilterUsage::class.java
                ) ?: emptyList()
            )
            pendingRuleExclude = savedInstanceState.getBoolean(PENDING_RULE_EXCLUDE_STATE, true)
        } else {
            val postFilterExtra: PostFilter? =
                BundleCompat.getParcelable(intent.extras ?: Bundle(), EXTRA_POST_FILTER, PostFilter::class.java)
            if (postFilterExtra == null) {
                postFilter = PostFilter()
                originalName = ""
            } else {
                postFilter = postFilterExtra
                originalName = if (fromSettings) postFilterExtra.name else ""
            }
            rules.addAll(PostFilterRules.toRules(postFilter))
            seedRulesFromIntent()
            loadUsages()
        }

        setUpAdapter()
        setUpInsets()

        setSupportActionBar(binding.toolbarCustomizePostFilterActivity)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        setToolbarGoToTop(binding.toolbarCustomizePostFilterActivity)

        binding.fabCustomizePostFilterActivity.setOnClickListener { showRuleSheet(null) }

        // The save runs in the ViewModel so its result — and the duplicate-name dialog — reaches the
        // live instance after a rotation, instead of firing on a dead one (CHUNKS deferred item 4). On
        // a back-press there is no observer, so a completed save no longer launches FilteredPosts at a
        // user who left; on a rotation the relaunched instance's observer picks it up.
        customizePostFilterViewModel.saveResult.observe(this) { result ->
            when (result) {
                is SavePostFilterResult.Success -> finishWithPostFilter()
                is SavePostFilterResult.Duplicate -> showDialog(
                    MaterialAlertDialogBuilder(this, R.style.MaterialAlertDialogTheme)
                        .setTitle(getString(R.string.duplicate_post_filter_dialog_title, postFilter.name))
                        .setMessage(R.string.duplicate_post_filter_dialog_message)
                        .setPositiveButton(R.string.override) { _, _ ->
                            saveToDatabase(postFilter.name)
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .create()
                )
                // The DB write threw (and rolled back); the guard has been released, so the user can retry.
                is SavePostFilterResult.Failure -> Snackbar.make(
                    binding.coordinatorLayoutCustomizePostFilterActivity,
                    R.string.save_post_filter_failed,
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun setUpAdapter() {
        val spanCount = resources.getInteger(R.integer.post_filter_rule_columns)
        // Targets can only be stored against a filter row, so they are offered only on the path that
        // saves one. The transient "filter this feed once" path has nothing to attach them to.
        adapter = PostFilterRulesRecyclerViewAdapter(
            this, mCustomThemeWrapper, postFilter, fromSettings, spanCount, this
        )
        val layoutManager = GridLayoutManager(this, spanCount)
        layoutManager.spanSizeLookup = adapter.spanSizeLookup
        binding.recyclerViewCustomizePostFilterActivity.layoutManager = layoutManager
        binding.recyclerViewCustomizePostFilterActivity.adapter = adapter
        adapter.setRules(rules)
        adapter.setUsages(usages)
    }

    private fun setUpInsets() {
        if (!isImmersiveInterfaceRespectForcedEdgeToEdge()) {
            return
        }
        if (isChangeStatusBarIconColor()) {
            addOnOffsetChangedListener(binding.appbarLayoutCustomizePostFilterActivity)
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val windowInsets = Utils.getInsets(insets, false, isForcedImmersiveInterface())
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())

            setMargins(
                binding.toolbarCustomizePostFilterActivity,
                windowInsets.left, windowInsets.top, windowInsets.right, IGNORE_MARGIN
            )
            binding.recyclerViewCustomizePostFilterActivity.setPadding(
                windowInsets.left,
                0,
                windowInsets.right,
                windowInsets.bottom + imeInsets.bottom +
                    resources.getDimensionPixelSize(R.dimen.fab_clearance)
            )
            // The inset is added to the FAB's 16dp margin rather than replacing it: with no inset on
            // that edge — portrait on the right, three-button nav consumed elsewhere — assigning the
            // raw inset pins the button flush against the screen edge.
            val fabMargin = Utils.convertDpToPixel(16f, this).toInt()
            setMargins(
                binding.fabCustomizePostFilterActivity,
                IGNORE_MARGIN, IGNORE_MARGIN, fabMargin + windowInsets.right,
                fabMargin + windowInsets.bottom + imeInsets.bottom
            )
            WindowInsetsCompat.CONSUMED
        }
    }

    /**
     * "Add to post filter" from a post, subreddit or user arrives as one-off extras; they become
     * rules like any other.
     */
    private fun seedRulesFromIntent() {
        addSeededRule(RuleField.SUBREDDIT, true, intent.getStringExtra(EXTRA_EXCLUDE_SUBREDDIT))
        addSeededRule(RuleField.SUBREDDIT, false, intent.getStringExtra(EXTRA_CONTAIN_SUBREDDIT))
        addSeededRule(RuleField.USER, true, intent.getStringExtra(EXTRA_EXCLUDE_USER))
        addSeededRule(RuleField.USER, false, intent.getStringExtra(EXTRA_CONTAIN_USER))
        addSeededRule(RuleField.FLAIR, true, intent.getStringExtra(EXTRA_EXCLUDE_FLAIR))
        addSeededRule(RuleField.FLAIR, false, intent.getStringExtra(EXTRA_CONTAIN_FLAIR))
        addSeededRule(RuleField.DOMAIN, true, domainOf(intent.getStringExtra(EXTRA_EXCLUDE_DOMAIN)))
        addSeededRule(RuleField.DOMAIN, false, domainOf(intent.getStringExtra(EXTRA_CONTAIN_DOMAIN)))
    }

    private fun addSeededRule(field: RuleField, exclude: Boolean, value: String?) {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isEmpty()) {
            return
        }
        PostFilterRules.addRule(rules, FilterRule(field, exclude, trimmed))
    }

    /**
     * Reduces a post URL to the domain this filter stores, or null when there is no domain to store.
     *
     * [Uri.getHost] is null for anything without a scheme — a bare "example.com" parses as a relative
     * path — and in that case the value already is the domain. It is also null for a scheme with no
     * authority ("https://"), which is neither a URL nor a domain, and returning null lets the caller
     * skip it rather than seeding an empty rule.
     */
    private fun domainOf(urlOrDomain: String?): String? {
        if (urlOrDomain.isNullOrEmpty()) {
            return null
        }
        val host = Uri.parse(urlOrDomain).host
        if (host != null) {
            return host
        }
        return if (urlOrDomain.contains("://")) null else urlOrDomain
    }

    private fun loadUsages() {
        if (!fromSettings || originalName.isEmpty()) {
            return
        }
        val name = originalName
        mExecutor.execute {
            val stored = mRedditDataRoomDatabase.postFilterUsageDao().getAllPostFilterUsage(name)
            Handler(Looper.getMainLooper()).post {
                if (isFinishing || isDestroyed) {
                    return@post
                }
                usages.clear()
                usages.addAll(stored)
                adapter.setUsages(usages)
            }
        }
    }

    // region Rules

    private fun showRuleSheet(rule: FilterRule?) {
        val fragment = AddPostFilterRuleBottomSheetFragment()
        fragment.arguments = Bundle().apply {
            putParcelable(AddPostFilterRuleBottomSheetFragment.EXTRA_RULE, rule)
            putParcelableArrayList(AddPostFilterRuleBottomSheetFragment.EXTRA_EXISTING_RULES, rules)
            putInt(
                AddPostFilterRuleBottomSheetFragment.EXTRA_INITIAL_FIELD,
                (rule?.field ?: RuleField.SUBREDDIT).ordinal
            )
            putBoolean(AddPostFilterRuleBottomSheetFragment.EXTRA_INITIAL_EXCLUDE, rule?.exclude ?: true)
        }
        fragment.show(supportFragmentManager, fragment.tag)
    }

    override fun onRuleClicked(rule: FilterRule) = showRuleSheet(rule)

    override fun onRuleRemoved(rule: FilterRule) {
        if (!rules.remove(rule)) {
            return
        }
        adapter.setRules(rules)
        Snackbar.make(
            binding.coordinatorLayoutCustomizePostFilterActivity,
            R.string.post_filter_rule_removed,
            Snackbar.LENGTH_LONG
        ).setAction(R.string.undo) {
            PostFilterRules.addRule(rules, rule)
            adapter.setRules(rules)
        }.show()
    }

    override fun onRuleSubmitted(original: FilterRule?, rule: FilterRule) {
        // The regex columns hold one pattern per polarity, so a second one has to displace the first
        // rather than silently vanish at save time.
        val displaced = rules.firstOrNull {
            rule.field == RuleField.TITLE_REGEX &&
                it.field == RuleField.TITLE_REGEX &&
                it.exclude == rule.exclude &&
                it != original
        }
        if (displaced != null) {
            showDialog(
                MaterialAlertDialogBuilder(this, R.style.MaterialAlertDialogTheme)
                    .setTitle(R.string.post_filter_rule_replace_regex_title)
                    .setMessage(
                        getString(
                            R.string.post_filter_rule_replace_regex_message,
                            getString(
                                if (rule.exclude) {
                                    R.string.post_filter_rule_exclude
                                } else {
                                    R.string.post_filter_rule_include
                                }
                            ),
                            displaced.value
                        )
                    )
                    .setPositiveButton(R.string.override) { _, _ ->
                        rules.remove(displaced)
                        commitRule(original, rule)
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .create()
            )
            return
        }
        commitRule(original, rule)
    }

    private fun commitRule(original: FilterRule?, rule: FilterRule) {
        original?.let { rules.remove(it) }
        PostFilterRules.addRule(rules, rule)
        adapter.setRules(rules)
    }

    override fun onRuleValuePickerRequested(field: RuleField, exclude: Boolean) {
        pendingRuleExclude = exclude
        when (field) {
            RuleField.SUBREDDIT -> startActivityForResult(
                Intent(this, SubredditMultiselectionActivity::class.java).putExtra(
                    SubredditMultiselectionActivity.EXTRA_GET_SELECTED_SUBREDDITS,
                    rules.filter { it.field == RuleField.SUBREDDIT && it.exclude == exclude }
                        .joinToString(",") { it.value }
                ),
                ADD_RULE_SUBREDDITS_REQUEST_CODE
            )
            RuleField.USER -> startActivityForResult(
                Intent(this, SearchActivity::class.java)
                    .putExtra(SearchActivity.EXTRA_SEARCH_ONLY_USERS, true)
                    .putExtra(SearchActivity.EXTRA_IS_MULTI_SELECTION, true),
                ADD_RULE_USERS_REQUEST_CODE
            )
            else -> Unit
        }
    }

    private fun addRules(field: RuleField, exclude: Boolean, values: List<String>) {
        var added = false
        for (value in values) {
            val trimmed = value.trim()
            if (trimmed.isEmpty()) {
                continue
            }
            added = PostFilterRules.addRule(rules, FilterRule(field, exclude, trimmed)) || added
        }
        if (added) {
            adapter.setRules(rules)
        }
    }

    // endregion

    // region Applies to

    override fun onAddUsageClicked() {
        val fragment = NewPostFilterUsageBottomSheetFragment()
        fragment.show(supportFragmentManager, fragment.tag)
    }

    override fun newPostFilterUsage(type: Int) {
        when (type) {
            PostFilterUsage.SUBREDDIT_TYPE,
            PostFilterUsage.USER_TYPE,
            PostFilterUsage.MULTIREDDIT_TYPE -> editUsageNameOfUsage(type, null, null)
            else -> addUsages(type, listOf(PostFilterUsage.NO_USAGE))
        }
    }

    override fun onUsageClicked(usage: PostFilterUsage) {
        when (usage.usage) {
            PostFilterUsage.SUBREDDIT_TYPE,
            PostFilterUsage.USER_TYPE,
            // The old entry is dropped only once the edit is confirmed. Removing it up front lost the
            // feed outright when the dialog was cancelled, while its chip stayed on screen.
            PostFilterUsage.MULTIREDDIT_TYPE -> editUsageNameOfUsage(usage.usage, usage.nameOfUsage, usage)
            else -> Unit
        }
    }

    override fun onUsageRemoved(usage: PostFilterUsage) {
        if (!usages.remove(usage)) {
            return
        }
        adapter.setUsages(usages)
        Snackbar.make(
            binding.coordinatorLayoutCustomizePostFilterActivity,
            R.string.post_filter_usage_removed,
            Snackbar.LENGTH_LONG
        ).setAction(R.string.undo) {
            // Through the same guard as any other add: the feed may have been re-added by hand while
            // the snackbar was up, and two chips for one row is not a state the table can hold.
            if (addUsage(usage)) {
                adapter.setUsages(usages)
            }
        }.show()
    }

    private fun addUsages(type: Int, namesOfUsage: List<String>) {
        var added = false
        for (nameOfUsage in namesOfUsage) {
            added = addUsage(PostFilterUsage(postFilter.name, type, nameOfUsage)) || added
        }
        if (added) {
            adapter.setUsages(usages)
        }
    }

    /** The table is keyed on (name, usage, name_of_usage); a repeat is the same row. */
    private fun addUsage(usage: PostFilterUsage): Boolean {
        if (usages.any { it.usage == usage.usage && it.nameOfUsage.equals(usage.nameOfUsage, true) }) {
            return false
        }
        usages.add(usage)
        return true
    }

    /**
     * One row per name: `name_of_usage` is matched exactly by
     * `PostFilterDao.getValidPostFilters`, so a comma-separated list in a single row would only ever
     * match a feed literally called "a,b".
     */
    private fun editUsageNameOfUsage(type: Int, nameOfUsage: String?, replacing: PostFilterUsage?) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_post_or_comment_filter_name_of_usage, null)
        val textInputLayout: TextInputLayout =
            dialogView.findViewById(R.id.text_input_layout_edit_post_or_comment_filter_name_of_usage_dialog)
        val editText: TextInputEditText =
            dialogView.findViewById(R.id.text_input_edit_text_edit_post_or_comment_filter_name_of_usage_dialog)
        val pickImageView: ImageView =
            dialogView.findViewById(R.id.add_subreddits_users_image_view_customize_post_filter_activity)
        usageDialogEditText = editText

        val primaryTextColor = mCustomThemeWrapper.primaryTextColor
        pickImageView.setImageDrawable(
            Utils.getTintedDrawable(this, R.drawable.ic_add_24dp, mCustomThemeWrapper.primaryIconColor)
        )
        textInputLayout.boxStrokeColor = primaryTextColor
        textInputLayout.defaultHintTextColor = ColorStateList.valueOf(primaryTextColor)
        editText.setTextColor(primaryTextColor)
        if (nameOfUsage != null && nameOfUsage != PostFilterUsage.NO_USAGE) {
            editText.setText(nameOfUsage)
        }
        editText.requestFocus()

        val titleStringId = when (type) {
            PostFilterUsage.USER_TYPE -> {
                editText.setHint(R.string.settings_tab_username)
                pickImageView.setOnClickListener {
                    startActivityForResult(
                        Intent(this, UserMultiselectionActivity::class.java).putExtra(
                            UserMultiselectionActivity.EXTRA_GET_SELECTED_USERS,
                            editText.text?.toString()?.trim().orEmpty()
                        ),
                        ADD_USAGE_USERS_REQUEST_CODE
                    )
                }
                R.string.user
            }
            PostFilterUsage.MULTIREDDIT_TYPE -> {
                editText.setHint(R.string.settings_tab_multi_reddit_name)
                pickImageView.visibility = View.GONE
                R.string.multi_reddit
            }
            else -> {
                editText.setHint(R.string.settings_tab_subreddit_name)
                pickImageView.setOnClickListener {
                    startActivityForResult(
                        Intent(this, SubredditMultiselectionActivity::class.java).putExtra(
                            SubredditMultiselectionActivity.EXTRA_GET_SELECTED_SUBREDDITS,
                            editText.text?.toString()?.trim().orEmpty()
                        ),
                        ADD_USAGE_SUBREDDITS_REQUEST_CODE
                    )
                }
                R.string.subreddit
            }
        }

        Utils.showKeyboard(this, Handler(Looper.getMainLooper()), editText)
        showDialog(
            MaterialAlertDialogBuilder(this, R.style.MaterialAlertDialogTheme)
                .setTitle(titleStringId)
                .setView(dialogView)
                .setPositiveButton(R.string.ok) { _, _ ->
                    Utils.hideKeyboard(this)
                    val entered = editText.text?.toString().orEmpty()
                        .split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                    replacing?.let { usages.remove(it) }
                    addUsages(type, entered.ifEmpty { listOf(PostFilterUsage.NO_USAGE) })
                    // addUsages only refreshes when it adds something, and editing a feed onto a name
                    // that is already listed adds nothing — the removed chip would stay on screen.
                    adapter.setUsages(usages)
                }
                .setNegativeButton(R.string.cancel, null)
                .setOnDismissListener {
                    Utils.hideKeyboard(this)
                    usageDialogEditText = null
                }
                .create()
        )
    }

    /** Merges picked names into the usage dialog's field, skipping ones already listed. */
    private fun appendToUsageDialog(names: List<String>) {
        val editText = usageDialogEditText ?: return
        val current = editText.text?.toString()?.trim().orEmpty()
        val existing = current.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val merged = existing + names.map { it.trim() }.filter { it.isNotEmpty() && it !in existing }
        editText.setText(merged.joinToString(","))
    }

    // endregion

    /** Keeps a raw dialog reachable so [onDestroy] can dismiss it instead of leaking its window. */
    private fun showDialog(dialog: AlertDialog) {
        activeDialog = dialog
        dialog.show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.customize_post_filter_activity, menu)
        if (fromSettings) {
            menu.findItem(R.id.action_save_customize_post_filter_activity).isVisible = false
        }
        applyMenuItemTheme(menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                return true
            }
            R.id.action_save_customize_post_filter_activity -> {
                constructPostFilter()
                finishWithPostFilter()
                return true
            }
            R.id.action_save_to_database_customize_post_filter_activity -> {
                constructPostFilter()
                if (postFilter.name.isEmpty()) {
                    Toast.makeText(this, R.string.post_filter_requires_a_name, Toast.LENGTH_LONG).show()
                } else {
                    saveToDatabase(originalName)
                }
                return true
            }
        }
        return false
    }

    private fun saveToDatabase(nameToReplace: String) {
        // Only the path that writes a filter row can write the usages that key off it.
        customizePostFilterViewModel.savePostFilter(
            postFilter, nameToReplace, if (fromSettings) ArrayList(usages) else null
        )
    }

    private fun finishWithPostFilter() {
        if (intent.getBooleanExtra(EXTRA_START_FILTERED_POSTS_WHEN_FINISH, false)) {
            val filteredPostsIntent = Intent(this, FilteredPostsActivity::class.java)
            filteredPostsIntent.putExtras(intent)
            filteredPostsIntent.putExtra(FilteredPostsActivity.EXTRA_CONSTRUCTED_POST_FILTER, postFilter)
            startActivity(filteredPostsIntent)
        } else {
            setResult(Activity.RESULT_OK, Intent().putExtra(RETURN_EXTRA_POST_FILTER, postFilter))
        }
        finish()
    }

    /**
     * The section rows write straight into [postFilter] as they are edited, so only the rule list
     * still has to be folded back into its columns.
     */
    private fun constructPostFilter() {
        postFilter.name = postFilter.name.trim()
        postFilter.maxAwards = -1
        postFilter.minAwards = -1
        PostFilterRules.applyRules(postFilter, rules)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) {
            return
        }
        when (requestCode) {
            ADD_RULE_SUBREDDITS_REQUEST_CODE ->
                addRules(RuleField.SUBREDDIT, pendingRuleExclude, selectedSubredditNames(data))
            ADD_RULE_USERS_REQUEST_CODE -> addRules(
                RuleField.USER,
                pendingRuleExclude,
                data.getStringArrayListExtra(SearchActivity.RETURN_EXTRA_SELECTED_USERNAMES).orEmpty()
            )
            ADD_USAGE_SUBREDDITS_REQUEST_CODE -> appendToUsageDialog(selectedSubredditNames(data))
            ADD_USAGE_USERS_REQUEST_CODE -> appendToUsageDialog(
                data.getStringArrayListExtra(UserMultiselectionActivity.EXTRA_RETURN_SELECTED_USERNAMES)
                    .orEmpty()
            )
        }
    }

    /**
     * [SubredditMultiselectionActivity] returns parcelled selections, not plain strings — reading it
     * as a string list silently yields nothing, which is what used to make the picker on the old
     * "Apply to" screen do nothing at all.
     */
    private fun selectedSubredditNames(data: Intent): List<String> =
        data.getParcelableArrayListExtra<SubredditWithSelection>(
            SubredditMultiselectionActivity.EXTRA_RETURN_SELECTED_SUBREDDITS
        ).orEmpty().map { it.name }

    override fun onDestroy() {
        // These prompts are raw AlertDialogs rather than DialogFragments, so one left showing across
        // a rotation leaks its window and this activity with it.
        activeDialog?.takeIf { it.isShowing }?.dismiss()
        activeDialog = null
        usageDialogEditText = null
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        constructPostFilter()
        outState.putParcelable(POST_FILTER_STATE, postFilter)
        outState.putString(ORIGINAL_NAME_STATE, originalName)
        outState.putParcelableArrayList(RULES_STATE, rules)
        outState.putParcelableArrayList(USAGES_STATE, usages)
        outState.putBoolean(PENDING_RULE_EXCLUDE_STATE, pendingRuleExclude)
    }

    override fun getDefaultSharedPreferences(): SharedPreferences = mSharedPreferences

    override fun getCurrentAccountSharedPreferences(): SharedPreferences = mCurrentAccountSharedPreferences

    override fun getCustomThemeWrapper(): CustomThemeWrapper = mCustomThemeWrapper

    override fun applyCustomTheme() {
        binding.coordinatorLayoutCustomizePostFilterActivity.setBackgroundColor(
            mCustomThemeWrapper.backgroundColor
        )
        applyAppBarLayoutAndCollapsingToolbarLayoutAndToolbarTheme(
            binding.appbarLayoutCustomizePostFilterActivity,
            binding.collapsingToolbarLayoutCustomizePostFilterActivity,
            binding.toolbarCustomizePostFilterActivity
        )
        applyAppBarScrollFlagsIfApplicable(binding.collapsingToolbarLayoutCustomizePostFilterActivity)
        applyFABTheme(binding.fabCustomizePostFilterActivity)
    }
}
