package com.shizi.ocr.ocr;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

/**
 * Rasterises a {@link ScanResult} on top of the scanned bitmap: one translucent
 * rounded frame per line, tinted per block, with the line number in a small
 * tag pinned to the top-left corner.
 *
 * <p>Line weights are derived from the bitmap width so the overlay looks the
 * same on a 720 px photo and a 2048 px one.</p>
 */
public final class BoxPainter {

    private static final int[] TINTS = {
            0xFF3B5BFE, // indigo
            0xFF00A99D, // teal
            0xFFE08000, // amber
            0xFFD6336C, // rose
            0xFF7C4DFF, // violet
            0xFF2F9E44, // green
    };

    private static final int FILL_ALPHA = 0x22;
    private static final float BASE_WIDTH = 1080f;

    private BoxPainter() {
    }

    /**
     * Returns a new, mutable bitmap equal to {@code source} plus the overlay.
     * The caller owns the result; {@code source} is left untouched.
     */
    public static Bitmap paint(Bitmap source, ScanResult result) {
        Bitmap output = source.copy(Bitmap.Config.ARGB_8888, true);
        if (output == null) {
            return source;
        }
        Canvas canvas = new Canvas(output);
        float unit = scaleFor(source.getWidth());

        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setStyle(Paint.Style.FILL);
        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(2.4f * unit);
        Paint badge = new Paint(Paint.ANTI_ALIAS_FLAG);
        badge.setStyle(Paint.Style.FILL);
        Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
        label.setColor(Color.WHITE);
        label.setTextSize(15f * unit);
        label.setTextAlign(Paint.Align.CENTER);
        label.setFakeBoldText(true);

        float corner = 7f * unit;

        for (int b = 0; b < result.blocks.size(); b++) {
            int tint = TINTS[b % TINTS.length];
            for (ScanLine line : result.blocks.get(b).lines) {
                if (!line.hasBounds()) {
                    continue;
                }
                RectF box = new RectF(line.bounds);
                box.inset(-3f * unit, -3f * unit);

                fill.setColor(withAlpha(tint, FILL_ALPHA));
                canvas.drawRoundRect(box, corner, corner, fill);

                stroke.setColor(tint);
                canvas.drawRoundRect(box, corner, corner, stroke);

                drawIndexTag(canvas, box, line.index, tint, unit, badge, label);
            }
        }
        return output;
    }

    private static void drawIndexTag(Canvas canvas, RectF box, int index,
                                     int tint, float unit, Paint badge, Paint label) {
        String text = String.valueOf(index);
        float padding = 5f * unit;
        float height = 18f * unit;
        float width = Math.max(height, label.measureText(text) + padding * 2f);
        float left = box.left;
        float top = box.top - height - 2f * unit;
        if (top < 0f) {
            top = box.top + 2f * unit;
        }
        RectF tag = new RectF(left, top, left + width, top + height);
        float radius = height / 2f;

        badge.setColor(tint);
        canvas.drawRoundRect(tag, radius, radius, badge);

        float baseline = tag.centerY() - (label.descent() + label.ascent()) / 2f;
        canvas.drawText(text, tag.centerX(), baseline, label);
    }

    /** Keeps stroke and text sizes proportional to the image size. */
    private static float scaleFor(int bitmapWidth) {
        float unit = bitmapWidth / BASE_WIDTH;
        if (unit < 0.55f) {
            return 0.55f;
        }
        if (unit > 2.6f) {
            return 2.6f;
        }
        return unit;
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }
}
