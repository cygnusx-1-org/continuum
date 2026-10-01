package ml.docilealligator.infinityforreddit.markdown.commentface

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The cascade that turns a subreddit stylesheet and a link into a comment face (issue #432).
 *
 * The r/anime cases run against its real stylesheet, saved in
 * `src/test/resources/commentface/` — the faces in the issue's screenshot, whose sprite crops were
 * checked against old Reddit by eye when this was written. The other subreddits are excerpts of
 * their real rules, cut down to the ones each test is about.
 *
 * Re-fetch the r/anime fixture with an OAuth GET of `/r/anime/about/stylesheet.json?raw_json=1`;
 * `stylesheet` goes in `anime.css` and each `images` entry is a `name<TAB>url` line of
 * `anime.images`.
 */
class CommentFaceStylesheetTest {

    private val anime: CommentFaceStylesheet by lazy {
        CommentFaceStylesheet.parse(resource("anime.css"), animeImages)
    }

    private val animeImages: Map<String, String> by lazy {
        resource("anime.images").lines().filter { it.isNotBlank() }.associate {
            val (name, url) = it.split('\t')
            name to url
        }
    }

    private fun resource(name: String): String {
        val path = "commentface/$name"
        val stream = checkNotNull(javaClass.classLoader?.getResourceAsStream(path)) { "missing fixture $path" }
        return stream.bufferedReader().use { it.readText() }
    }

    private fun face(stylesheet: CommentFaceStylesheet, href: String,
                     context: CommentFaceContext = CommentFaceContext.COMMENT, title: String? = null): CommentFace {
        val face = stylesheet.resolve(href, title, context)
        assertNotNull("$href should be a face", face)
        return face!!
    }

    private fun px(value: Float) = CssLength(value, 0f)

    // ---- r/anime, the issue's own thread ----

    @Test
    fun `schemingsaten takes its size from one rule and its position from a later one`() {
        val face = face(anime, "#schemingsaten")
        assertEquals(animeImages.getValue("1"), face.imageUrl)
        assertEquals(129f, face.boxWidth)
        assertEquals(121f, face.boxHeight)
        // The size rule's `background` shorthand reset the base rule's -150px; the position rule
        // after it set 140px, which wraps round the repeating sprite.
        assertEquals(px(140f), face.background.positionX)
        assertEquals(px(0f), face.background.positionY)
        assertTrue(face.background.repeatX && face.background.repeatY)
    }

    @Test
    fun `hikariactually's one-value position centres it vertically`() {
        val face = face(anime, "#hikariactually")
        assertEquals(144f, face.boxWidth)
        assertEquals(116f, face.boxHeight)
        assertEquals(px(7597f), face.background.positionX)
        assertEquals(CssLength(0f, 0.5f), face.background.positionY)
    }

    @Test
    fun `bonk comes from a different sprite`() {
        val face = face(anime, "#bonk")
        assertEquals(animeImages.getValue("M"), face.imageUrl)
        assertEquals(180f, face.boxWidth)
        assertEquals(121f, face.boxHeight)
        assertEquals(px(0f), face.background.positionX)
        assertEquals(px(0f), face.background.positionY)
    }

    @Test
    fun `r anime's selectors ignore case`() {
        assertEquals(face(anime, "#schemingsaten"), face(anime, "#SchemingSaten"))
    }

    @Test
    fun `a name r anime has no rule for still gets its base face`() {
        // `.md [href^="#"]` gives every # link a face, which is what old Reddit showed for a typo.
        val face = face(anime, "#notarealfacename")
        assertEquals(120f, face.boxWidth)
        assertEquals(154f, face.boxHeight)
        assertEquals(px(-150f), face.background.positionX)
    }

    @Test
    fun `the spoiler link is reset to an ordinary link`() {
        assertNull(anime.resolve("#s", "spoiler text", CommentFaceContext.COMMENT))
    }

    @Test
    fun `links that are not relative never match`() {
        assertNull(anime.resolve("https://www.reddit.com/r/anime", null, CommentFaceContext.COMMENT))
    }

    @Test
    fun `r anime captions are white, outlined, centred, and bold text sits on the bottom edge`() {
        val caption = checkNotNull(face(anime, "#schemingsaten").caption)
        assertEquals(18f, caption.fontSize)
        assertEquals(0xFFFFFFFF.toInt(), caption.color)
        assertEquals(CommentFaceCaptionStyle.Outline(0xFF000000.toInt()), caption.outline)
        assertEquals(CommentFaceCaptionStyle.Align.CENTER, caption.align)
        val strong = caption.strong
        assertEquals(CommentFaceStrongStyle.Anchor.BOTTOM, strong.anchor)
        assertEquals(4f, strong.offset)
        // font-weight: inherit, and the link itself is not bold.
        assertEquals(false, strong.bold)
    }

    @Test
    fun `a title does not change the face`() {
        assertEquals(face(anime, "#bonk"), face(anime, "#bonk", title = "Hugh Laurie"))
    }

    @Test
    fun `resolving is memoised`() {
        assertSame(face(anime, "#bonk"), face(anime, "#bonk"))
    }

    // ---- other subreddits' conventions ----

    @Test
    fun `r visualnovels matches case-sensitively and hides the text`() {
        val stylesheet = CommentFaceStylesheet.parse("""
            a[href="#somad"], a[href="#trollin"],a[href="#WAHAHAHA"] { background: url(%%vnsprites%%); content: ""; font-size: 0 !important; cursor: default; display: inline-block; margin: 0 2px; }
            a[href="#WAHAHAHA"] { background-position:-109px -170px; width:79px; height:85px; }
        """.trimIndent(), mapOf("vnsprites" to "https://b.thumbs.redditmedia.com/vn.png"))

        val face = face(stylesheet, "#WAHAHAHA")
        assertEquals("https://b.thumbs.redditmedia.com/vn.png", face.imageUrl)
        assertEquals(79f, face.boxWidth)
        assertEquals(85f, face.boxHeight)
        assertEquals(px(-109f), face.background.positionX)
        assertEquals(px(-170f), face.background.positionY)
        assertNull("font-size: 0 hides the caption", face.caption)
        assertNull(stylesheet.resolve("#wahahaha", null, CommentFaceContext.COMMENT))
    }

    @Test
    fun `r leagueoflegends gives every face its size from one prefix rule`() {
        val stylesheet = CommentFaceStylesheet.parse("""
            .md a[href^="#face-"] { display: inline-block; width: 100px; height: 101px; background-image: url(%%comment-faces%%); font-size: 0; }
            .md a[href="#face-wink"] { background-position: 0 0; }
            .md a[href="#face-ok"] { background-position: -100px 0; }
        """.trimIndent(), mapOf("comment-faces" to "https://a.thumbs.redditmedia.com/lol.png"))

        val face = face(stylesheet, "#face-ok")
        assertEquals(100f, face.boxWidth)
        assertEquals(101f, face.boxHeight)
        assertEquals(px(-100f), face.background.positionX)
        assertNull(face.caption)
    }

    @Test
    fun `r soccer's flags are hidden in comments and shown in posts`() {
        val stylesheet = CommentFaceStylesheet.parse("""
            .flair,a.title:before,.titlebox .author:before,a[href^="#country"],a[href^="#icon"],.md a[href]:before { background-color:transparent;background-repeat:no-repeat;background-position:0 0;padding:0;border-width:0;border-radius:0;vertical-align:middle;color:transparent !important;overflow:hidden }
            a[href^="#icon"],a[href^="#country"],a[href^="#bar"] { display:inline-block;cursor:default;pointer-events:none }
            a[href^="#country"] { width:20px;height:20px;background-size:420px 280px;background-image:url(%%country-flags%%) }
            .comment a[href^="#icon"],.comment a[href^="#country"],.comment a[href^="#bar"] { display:none !important }
            h1 a[href^="#country"] { width:40px;height:40px;background-size:840px 560px }
            a[href="#country-argentina"] { background-position: -40px -20px }
        """.trimIndent(), mapOf("country-flags" to "https://b.thumbs.redditmedia.com/flags.png"))

        assertSame(CommentFace.HIDDEN, stylesheet.resolve("#country-argentina", null, CommentFaceContext.COMMENT))

        val face = face(stylesheet, "#country-argentina", CommentFaceContext.POST)
        // Not the h1 rule's 40px: a heading is not where the link is.
        assertEquals(20f, face.boxWidth)
        assertEquals(20f, face.boxHeight)
        assertEquals(CommentFaceBackground.SizeMode.EXPLICIT, face.background.sizeMode)
        assertEquals(px(420f), face.background.sizeX)
        assertEquals(px(280f), face.background.sizeY)
        assertEquals(px(-40f), face.background.positionX)
        assertEquals(px(-20f), face.background.positionY)
        assertEquals(false, face.background.repeatX)
        assertEquals(CommentFace.VerticalAlign.MIDDLE, face.verticalAlign)
        assertNull("color: transparent hides the caption", face.caption)
    }

    @Test
    fun `r gintama's padding grows the box and its not() exclusions are evaluated`() {
        val stylesheet = CommentFaceStylesheet.parse("""
            .md a[href^="/"]:not([href^="/r/"]):not([href^="/u/"]):not([href="/s"]):not([href^="/message"]), .thing .md:not(.RESdupeimg) > a[href^="#"] { display: inline-block; position: relative; padding-top: 2px; text-align: center; font-family: arial, sans-serif; font-size: 18px; color: white !important; text-shadow: 1px 1px 0 #000, 1px -1px 0 #000; }
            .thing .md a[href^="/"]:not([href^="/r/"]):not([href^="/u/"]):not([href="/s"]):not([href^="/message"]) > strong, .thing .md a[href^="#"] > strong { display: block; width: 100%; position: absolute; bottom: 4px; font-weight: normal; }
            a[href="#pretty"] { display: inline-block; width: 150px; height: 150px; background: url(%%commentfaces%%) no-repeat; background-position: -5px -5px; width: 150px; height: 150px; }
            .md [href^="#"]:not(ul li a) { text-align: center; color: white; text-shadow: 0px 0px 2px #000; }
            a[href="/r/gintama"] { display: inline-block; width: 50px; height: 50px; background: url(%%commentfaces%%); }
        """.trimIndent(), mapOf("commentfaces" to "https://b.thumbs.redditmedia.com/gintama.png"))

        val face = face(stylesheet, "#pretty")
        assertEquals(150f, face.boxWidth)
        assertEquals(152f, face.boxHeight)
        assertEquals(2f, face.paddingTop)
        assertEquals(false, face.background.repeatX)
        val caption = checkNotNull(face.caption)
        assertEquals(0xFFFFFFFF.toInt(), caption.color)
        assertEquals(CommentFaceStrongStyle.Anchor.BOTTOM, caption.strong.anchor)
        assertEquals(false, caption.strong.bold)
    }

    @Test
    fun `a shared rule's whole-sprite width is overridden per face, and left alone it is no face`() {
        val stylesheet = CommentFaceStylesheet.parse("""
            a[href="#akagi1"],a[href="#hachi"] { background:url(%%Comment-Faces%%) no-repeat;display:inline-block;height:120px;width:5400px }
            a[href="#akagi1"] { width:120px;height:120px;background-position:-120px 0 }
        """.trimIndent(), mapOf("Comment-Faces" to "https://b.thumbs.redditmedia.com/kc.png"))

        val face = face(stylesheet, "#akagi1")
        assertEquals(120f, face.boxWidth)
        assertEquals(px(-120f), face.background.positionX)
        assertNull("a 5400px box is the sprite sheet, not a face", stylesheet.resolve("#hachi", null, CommentFaceContext.COMMENT))
    }

    // ---- the cascade itself ----

    private val images = mapOf("sprite" to "https://b.thumbs.redditmedia.com/sprite.png")
    private val base = ".md a[href^=\"#\"] { display: inline-block; width: 50px; height: 60px; background: url(%%sprite%%); }\n"

    @Test
    fun `a more specific rule wins over a later, less specific one`() {
        val stylesheet = CommentFaceStylesheet.parse(base + """
            .md a[href="#x"] { width: 70px; }
            a[href="#x"] { width: 90px; }
        """.trimIndent(), images)
        assertEquals(70f, face(stylesheet, "#x").boxWidth)
    }

    @Test
    fun `important beats specificity`() {
        val stylesheet = CommentFaceStylesheet.parse(base + """
            .md a[href="#x"] { width: 70px; }
            a[href="#x"] { width: 90px !important; }
        """.trimIndent(), images)
        assertEquals(90f, face(stylesheet, "#x").boxWidth)
    }

    @Test
    fun `hover and pseudo-element rules do not apply`() {
        val stylesheet = CommentFaceStylesheet.parse(base + """
            .md a[href="#x"]:hover { width: 70px; }
            .md a[href="#x"]::before { width: 80px; }
            .md a[href="#x"]:after { width: 90px; }
        """.trimIndent(), images)
        assertEquals(50f, face(stylesheet, "#x").boxWidth)
    }

    @Test
    fun `a sidebar rule reaches the sidebar and nowhere else`() {
        val stylesheet = CommentFaceStylesheet.parse("""
            .side a[href="#banner"] { display: block; width: 300px; height: 100px; background: url(%%sprite%%); }
        """.trimIndent(), images)
        assertNull(stylesheet.resolve("#banner", null, CommentFaceContext.COMMENT))
        assertEquals(300f, face(stylesheet, "#banner", CommentFaceContext.SIDEBAR).boxWidth)
    }

    @Test
    fun `media queries are evaluated for a desktop window`() {
        val stylesheet = CommentFaceStylesheet.parse(base + """
            @media screen and (min-width: 700px) { .md a[href="#x"] { width: 70px; } }
            @media (-webkit-min-device-pixel-ratio: 2), (min-resolution: 192dpi) { .md a[href="#x"] { width: 140px; } }
            @media print { .md a[href="#x"] { height: 10px; } }
            @keyframes spin { from { width: 1px; } to { width: 2px; } }
        """.trimIndent(), images)
        val face = face(stylesheet, "#x")
        assertEquals(70f, face.boxWidth)
        assertEquals(60f, face.boxHeight)
    }

    @Test
    fun `an inline link ignores its width and height, so is no face`() {
        val stylesheet = CommentFaceStylesheet.parse("""
            .md a[href="#x"] { width: 50px; height: 60px; background: url(%%sprite%%); }
        """.trimIndent(), images)
        assertNull(stylesheet.resolve("#x", null, CommentFaceContext.COMMENT))
    }

    @Test
    fun `comments, strings and odd selectors do not derail the parse`() {
        val stylesheet = CommentFaceStylesheet.parse("""
            /* .md a[href="#x"] { width: 999px; } */
            .md a[title="{"]::after { content: "}"; }
            .md a[href=#invalid] { width: 1px; }
            a[href="#x"] + a { width: 2px; }
        """.trimIndent() + "\n" + base, images)
        assertEquals(50f, face(stylesheet, "#x").boxWidth)
    }

    @Test
    fun `a stylesheet with nothing to paint is empty`() {
        val stylesheet = CommentFaceStylesheet.parse(".md a { color: red; } .side { width: 300px; }", emptyMap())
        assertTrue(stylesheet.isEmpty)
        assertNull(stylesheet.resolve("#x", null, CommentFaceContext.COMMENT))
    }

    @Test
    fun `an image from outside Reddit is not loaded`() {
        val stylesheet = CommentFaceStylesheet.parse("""
            .md a[href="#x"] { display: inline-block; width: 50px; height: 60px; background: url(https://example.com/x.png); }
        """.trimIndent(), emptyMap())
        assertNull(stylesheet.resolve("#x", null, CommentFaceContext.COMMENT))
    }

    @Test
    fun `subreddit names normalise to a cache key`() {
        assertEquals("anime", CommentFaceRepository.normalize("Anime"))
        assertEquals("anime", CommentFaceRepository.normalize("r/anime"))
        assertEquals("anime", CommentFaceRepository.normalize("/r/anime"))
        assertNull(CommentFaceRepository.normalize(null))
        assertNull(CommentFaceRepository.normalize("../etc"))
    }
}
