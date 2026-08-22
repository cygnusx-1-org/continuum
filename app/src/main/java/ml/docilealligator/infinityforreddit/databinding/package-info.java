/**
 * Marker package-info for ViewBinding's generated {@code *Binding} classes.
 *
 * <p>Those classes are machine-written into {@code build/generated}, so they cannot carry a
 * {@code @NullMarked} package-info of their own. This file supplies one for the package, which is
 * what makes NullAway honour the {@code @Nullable} that ViewBinding puts on any view missing from
 * at least one layout configuration. Without it, {@code OnlyNullMarked} leaves the whole package
 * unannotated and every such field reads as non-null -- which is how the crash in issue #369
 * (dereferencing a view present only in {@code layout/}) compiled clean.
 *
 * <p>Note that {@code AcknowledgeRestrictiveAnnotations} does <em>not</em> substitute for this:
 * it applies to method returns and parameters, and ViewBinding exposes views as fields.
 *
 * <p>Findings <em>inside</em> the generated sources are suppressed by the Error Prone
 * {@code excludedPaths} setting in app/build.gradle; what this file enables is the check at our
 * own call sites.
 */
@NullMarked
package ml.docilealligator.infinityforreddit.databinding;

import org.jspecify.annotations.NullMarked;
