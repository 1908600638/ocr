package com.shizi.ocr.ocr;

import android.graphics.Bitmap;

import java.util.Collections;
import java.util.List;

/**
 * Immutable outcome of a single scan: the geometry, the flattened line list and
 * the plain-text projection that gets copied to the clipboard.
 */
public final class ScanResult {

    /** The bitmap that was scanned; owned by the caller. */
    public final Bitmap source;

    public final List<ScanBlock> blocks;

    /** All lines in reading order, flattened across blocks. */
    public final List<ScanLine> lines;

    /** Lines joined with {@code \n}. */
    public final String text;

    ScanResult(Bitmap source, List<ScanBlock> blocks, List<ScanLine> lines, String text) {
        this.source = source;
        this.blocks = Collections.unmodifiableList(blocks);
        this.lines = Collections.unmodifiableList(lines);
        this.text = text;
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }

    public int lineCount() {
        return lines.size();
    }

    /** Character count excluding whitespace, which reads better in the UI. */
    public int characterCount() {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isWhitespace(text.charAt(i))) {
                count++;
            }
        }
        return count;
    }
}
