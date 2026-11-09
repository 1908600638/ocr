package com.shizi.ocr.ocr;

import android.graphics.Rect;

import java.util.Collections;
import java.util.List;

/**
 * One recognized text line and the area it covers in the source bitmap.
 */
public final class ScanLine {

    /** 1-based position in reading order, shared across all blocks. */
    public final int index;

    /** Recognized content, exactly as produced by the recognizer. */
    public final String text;

    /** Union box of the line; may be {@code null} when the model has no geometry. */
    public final Rect bounds;

    /** Per-word boxes, used for finer overlays. Never {@code null}. */
    public final List<Rect> words;

    ScanLine(int index, String text, Rect bounds, List<Rect> words) {
        this.index = index;
        this.text = text;
        this.bounds = bounds;
        this.words = Collections.unmodifiableList(words);
    }

    public boolean hasBounds() {
        return bounds != null && bounds.width() > 0 && bounds.height() > 0;
    }
}
