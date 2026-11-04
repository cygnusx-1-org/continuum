package ml.docilealligator.infinityforreddit.lint

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.Vendor
import com.android.tools.lint.detector.api.CURRENT_API
import com.android.tools.lint.detector.api.Issue

/**
 * Registry for Reddit Jr.'s own lint checks. Referenced from the jar manifest
 * (`Lint-Registry-v2`, see lint-checks/build.gradle) and loaded by the app module's
 * `lintChecks project(':lint-checks')` dependency.
 */
class RedditJrIssueRegistry : IssueRegistry() {

    override val issues: List<Issue> = listOf(LayoutVariantIdParityDetector.ISSUE)

    /** Lint API this registry was compiled against — must track the lint-api dependency. */
    override val api: Int = CURRENT_API

    /**
     * Oldest lint API the checks still work on. Nothing here uses recent API, so accept older
     * lint rather than making a version skew silently drop the check.
     */
    override val minApi: Int = 10

    override val vendor: Vendor = Vendor(
        vendorName = "Reddit Jr.",
        identifier = "redditjr",
        feedbackUrl = "https://github.com/jayblah/redditjr/issues",
    )
}
