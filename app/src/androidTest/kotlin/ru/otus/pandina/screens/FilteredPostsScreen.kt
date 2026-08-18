package ru.otus.pandina.screens

import android.view.View
import androidx.test.espresso.matcher.ViewMatchers
import com.kaspersky.kaspresso.screens.KScreen
import io.github.kakaocup.kakao.image.KImageView
import io.github.kakaocup.kakao.recycler.KRecyclerItem
import io.github.kakaocup.kakao.recycler.KRecyclerView
import io.github.kakaocup.kakao.screen.Screen
import io.github.kakaocup.kakao.text.KButton
import io.github.kakaocup.kakao.text.KTextView
import ml.docilealligator.infinityforreddit.R
import ml.docilealligator.infinityforreddit.activities.FilteredPostsActivity
import org.hamcrest.Matcher
import org.hamcrest.Matchers.anyOf

object FilteredPostsScreen : KScreen<FilteredPostsScreen>() {

    val filterButton = KButton { withId(R.id.fab_filtered_thing_activity) }

    val moreOptions = KImageView { withContentDescription("More options")}

    val changePostLayout = KTextView { withText("Change Post Layout")}

    val postFragmentList = KRecyclerView(
        builder = { withId(R.id.recycler_view_post_fragment) },
        itemTypeBuilder = { itemType(::PostFragmentItem) }
    )

    class PostFragmentItem(parent: Matcher<View>) : KRecyclerItem<PostFragmentItem>(parent) {
        /**
         * The title, whichever card-family layout the adapter inflated for this post. One matcher
         * over all of them rather than a try/catch over several views: exactly one of these ids
         * exists per item, so this stays unambiguous while still failing when the title is missing
         * or empty -- a catch around the assertion would instead hide a genuinely blank title.
         */
        val title = KTextView(parent) {
            // ViewMatchers.withId, spelled out: inside this builder a bare withId() resolves to
            // Kakao's own member, which returns Unit rather than a matcher.
            withMatcher(
                anyOf(
                    ViewMatchers.withId(R.id.title_text_view_item_post_with_preview),
                    ViewMatchers.withId(R.id.title_text_view_item_post_gallery_type),
                    ViewMatchers.withId(R.id.title_text_view_item_post_video_type_autoplay),
                    ViewMatchers.withId(R.id.title_text_view_item_post_text_type),
                )
            )
        }

        val image = KImageView(parent) { withId(R.id.image_view_item_post_with_preview) }
        val galleryImage = KImageView(parent) { withResourceName("image_view_item_post_gallery")}
    }

    override val layoutId: Int? = R.layout.activity_filtered_thing
    override val viewClass: Class<*>? = FilteredPostsActivity::class.java
}
