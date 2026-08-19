package ru.otus.pandina.screens

import com.kaspersky.kaspresso.screens.KScreen
import io.github.kakaocup.kakao.check.KCheckBox
import io.github.kakaocup.kakao.edit.KEditText
import io.github.kakaocup.kakao.text.KButton
import io.github.kakaocup.kakao.toolbar.KToolbar
import ml.docilealligator.infinityforreddit.R

object CustomizePostFilterScreen : KScreen<CustomizePostFilterScreen>() {

    val toolBar = KToolbar { withId(R.id.toolbar_customize_post_filter_activity) }

    val customizeFilterEditText = KEditText { withId(R.id.name_text_input_edit_text_customize_post_filter_activity) }

    // Post types are filter chips rather than a labelled switch per row, so one KCheckBox covers
    // both the label and the checked state that used to need a TextView and a KSwitch each.
    val textFilterChip = KCheckBox { withId(R.id.post_type_text_chip_item_post_filter_post_types) }

    val linkFilterChip = KCheckBox { withId(R.id.post_type_link_chip_item_post_filter_post_types) }

    val onlyNsfwChip = KCheckBox { withId(R.id.only_nsfw_chip_item_post_filter_show_only) }

    val saveButton = KButton { withId(R.id.action_save_customize_post_filter_activity) }
    override val layoutId: Int? = null
    override val viewClass: Class<*>? = null
}
