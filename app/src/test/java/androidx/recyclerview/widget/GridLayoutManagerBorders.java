package androidx.recyclerview.widget;

/**
 * GridLayoutManager's own column arithmetic, which is package-private, for GalleryGridCellTest to
 * check its copy against.
 */
public final class GridLayoutManagerBorders {
    private GridLayoutManagerBorders() {
    }

    public static int[] of(int spanCount, int totalSpace) {
        return GridLayoutManager.calculateItemBorders(null, spanCount, totalSpace);
    }
}
