package com.shizi.ocr.ocr;

import android.graphics.Rect;

import java.util.Collections;
import java.util.List;

/**
 * A paragraph-sized group of lines. Blocks are kept so the overlay can colour
 * neighbouring lines consistently and so callers can reconstruct layout.
 */
public final class ScanBlock {

    /** Union box of the block; never {@code null}. */
    public final Rect bounds;

    public final List<ScanLine> lines;

    ScanBlock(Rect bounds, List<ScanLine> lines) {
        this.bounds = bounds;
        this.lines = Collections.unmodifiableList(lines);
    }
}
