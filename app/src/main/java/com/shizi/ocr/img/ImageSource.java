package com.shizi.ocr.img;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;

import androidx.annotation.NonNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Decodes a content {@link Uri} into a bitmap that is ready for recognition:
 * EXIF rotation applied and the longest edge capped at {@link #MAX_EDGE}.
 *
 * <p>Decoding a 12 MP JPEG straight into memory would cost ~48 MB, and the
 * recognizer gains nothing above a couple of thousand pixels, so the source is
 * downsampled at decode time rather than after the fact.</p>
 */
public final class ImageSource {

    /** Longest edge handed to the recognizer. */
    public static final int MAX_EDGE = 2048;

    /** Hard ceiling on the bytes read from the provider. */
    private static final int MAX_BYTES = 32 * 1024 * 1024;

    private ImageSource() {
    }

    /**
     * @return a bitmap owned by the caller, never {@code null}
     * @throws IOException if the stream is empty, too large or not a bitmap
     */
    @NonNull
    public static Bitmap decode(@NonNull Context context, @NonNull Uri uri) throws IOException {
        byte[] raw = readBytes(context.getContentResolver(), uri);

        BitmapFactory.Options probe = new BitmapFactory.Options();
        probe.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(raw, 0, raw.length, probe);
        if (probe.outWidth <= 0 || probe.outHeight <= 0) {
            throw new IOException("Not a decodable bitmap: " + uri);
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSizeFor(probe.outWidth, probe.outHeight);
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap decoded = BitmapFactory.decodeByteArray(raw, 0, raw.length, options);
        if (decoded == null) {
            throw new IOException("BitmapFactory returned null: " + uri);
        }

        decoded = shrinkToLimit(decoded);
        return applyExifRotation(raw, decoded);
    }

    private static byte[] readBytes(ContentResolver resolver, Uri uri) throws IOException {
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) {
                throw new IOException("No stream for " + uri);
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream(64 * 1024);
            byte[] chunk = new byte[16 * 1024];
            int read;
            while ((read = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
                if (buffer.size() > MAX_BYTES) {
                    throw new IOException("Image exceeds " + MAX_BYTES + " bytes");
                }
            }
            return buffer.toByteArray();
        }
    }

    /** Largest power-of-two downsample that still leaves at least MAX_EDGE. */
    private static int sampleSizeFor(int width, int height) {
        int longest = Math.max(width, height);
        int sample = 1;
        while (longest / (sample * 2) >= MAX_EDGE) {
            sample *= 2;
        }
        return sample;
    }

    /** Exact resize for sources whose longest edge is still above the limit. */
    private static Bitmap shrinkToLimit(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int longest = Math.max(width, height);
        if (longest <= MAX_EDGE) {
            return bitmap;
        }
        float ratio = (float) MAX_EDGE / longest;
        int targetW = Math.max(1, Math.round(width * ratio));
        int targetH = Math.max(1, Math.round(height * ratio));
        Bitmap scaled = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true);
        if (scaled != bitmap) {
            bitmap.recycle();
        }
        return scaled;
    }

    private static Bitmap applyExifRotation(byte[] raw, Bitmap bitmap) {
        int orientation;
        try {
            ExifInterface exif = new ExifInterface(new ByteArrayInputStream(raw));
            orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        } catch (IOException | RuntimeException e) {
            return bitmap;
        }

        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.postRotate(90f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.postRotate(180f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.postRotate(270f);
                break;
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                matrix.postScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                matrix.postScale(1f, -1f);
                break;
            default:
                return bitmap;
        }

        Bitmap rotated = Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        if (rotated != bitmap) {
            bitmap.recycle();
        }
        return rotated;
    }
}
