package ml.docilealligator.infinityforreddit.resume

import android.os.Bundle
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Type-tagged JSON for the small bundles a resume snapshot is made of.
 *
 * A `Bundle` cannot simply be written to disk. Marshalling one produces a `Parcel`, whose layout is
 * a private implementation detail that changes between releases, so a snapshot written by one build
 * and read by the next would be garbage rather than an error. Each value is therefore written as
 * `{"t": <tag>, "v": <value>}`, and reading one back re-creates it with the right `put`: the tag is
 * what makes an `Int` come back an `Int` rather than as whatever JSON's number rules would choose.
 *
 * Only the types below survive, which is enough because everything recorded here is an intent extra
 * or a screen's own note to itself, and both are primitives by the time they get this far.
 */
internal object BundleJson {

    private const val KEY_TYPE = "t"
    private const val KEY_VALUE = "v"

    private const val TYPE_STRING = "s"
    private const val TYPE_INT = "i"
    private const val TYPE_LONG = "l"
    private const val TYPE_BOOLEAN = "b"
    private const val TYPE_FLOAT = "f"
    private const val TYPE_DOUBLE = "d"
    private const val TYPE_STRING_LIST = "sl"

    /**
     * Encode [bundle].
     *
     * In [lenient] mode an unsupported value is dropped: recorded state is advisory, and a screen
     * that cannot restore one field of it still restores the rest. Otherwise the whole bundle is
     * refused by returning null, which is what marks an activity unrelaunchable -- an intent
     * replayed without one of its extras is a different intent, and launching it would put the user
     * somewhere they never were.
     */
    @Suppress("DEPRECATION")
    fun toJson(bundle: Bundle, lenient: Boolean): JSONObject? {
        val out = JSONObject()
        try {
            for (key in bundle.keySet()) {
                val value = bundle.get(key)
                if (value == null) {
                    // An explicitly null extra carries nothing to restore and nothing to disagree
                    // about, so it is skipped rather than counted against the bundle.
                    continue
                }
                val type =
                    when (value) {
                        is String -> TYPE_STRING
                        is Int -> TYPE_INT
                        is Long -> TYPE_LONG
                        is Boolean -> TYPE_BOOLEAN
                        is Float -> TYPE_FLOAT
                        is Double -> TYPE_DOUBLE
                        is ArrayList<*> -> if (value.all { it is String }) TYPE_STRING_LIST else null
                        else -> null
                    }
                if (type == null) {
                    if (!lenient) {
                        return null
                    }
                    continue
                }
                val encoded =
                    JSONObject().put(KEY_TYPE, type).put(
                        KEY_VALUE,
                        if (type == TYPE_STRING_LIST) JSONArray(value as ArrayList<*>) else value,
                    )
                out.put(key, encoded)
            }
        } catch (e: JSONException) {
            return null
        }
        return out
    }

    /** Decode what [toJson] wrote. An entry with an unknown tag is skipped, not guessed at. */
    fun toBundle(json: JSONObject): Bundle {
        val bundle = Bundle()
        for (key in json.keys()) {
            val entry = json.optJSONObject(key) ?: continue
            when (entry.optString(KEY_TYPE)) {
                TYPE_STRING -> bundle.putString(key, entry.optString(KEY_VALUE))
                TYPE_INT -> bundle.putInt(key, entry.optInt(KEY_VALUE))
                TYPE_LONG -> bundle.putLong(key, entry.optLong(KEY_VALUE))
                TYPE_BOOLEAN -> bundle.putBoolean(key, entry.optBoolean(KEY_VALUE))
                TYPE_FLOAT -> bundle.putFloat(key, entry.optDouble(KEY_VALUE).toFloat())
                TYPE_DOUBLE -> bundle.putDouble(key, entry.optDouble(KEY_VALUE))
                TYPE_STRING_LIST -> {
                    val array = entry.optJSONArray(KEY_VALUE) ?: continue
                    val list = ArrayList<String>(array.length())
                    for (i in 0 until array.length()) {
                        list.add(array.optString(i))
                    }
                    bundle.putStringArrayList(key, list)
                }
            }
        }
        return bundle
    }

    /**
     * Whether two bundles describe the same thing.
     *
     * Key by key, not whole-document string equality: a `JSONObject` preserves the insertion order
     * it was built in, and two bundles with the same contents can enumerate their keys in different
     * orders. Each per-key object has its two fields in a fixed order, so comparing those as strings
     * is stable.
     */
    fun sameContents(a: Bundle, b: Bundle): Boolean {
        val left = toJson(a, lenient = false) ?: return false
        val right = toJson(b, lenient = false) ?: return false
        if (left.length() != right.length()) {
            return false
        }
        for (key in left.keys()) {
            val one = left.optJSONObject(key) ?: return false
            val other = right.optJSONObject(key) ?: return false
            if (one.toString() != other.toString()) {
                return false
            }
        }
        return true
    }
}
