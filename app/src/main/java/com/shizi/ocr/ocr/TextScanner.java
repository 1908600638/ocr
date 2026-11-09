package com.shizi.ocr.ocr;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Runs ML Kit's on-device Chinese recognizer and hands back both the structured
 * result and a bitmap with the boxes already drawn on it.
 *
 * <p>Inference itself is asynchronous inside ML Kit, but turning the model
 * output into {@link ScanResult} and rasterising the overlay is CPU work, so it
 * happens on a private single-thread executor and the callback is delivered on
 * the main thread.</p>
 *
 * <p>Instances hold a recognizer; call {@link #close()} from the host's
 * {@code onDestroy} to release it.</p>
 */
public class TextScanner {

    /** Callback delivered on the main thread. */
    public interface Callback {

        void onScanned(@NonNull ScanResult result, @NonNull Bitmap overlay);

        void onFailed(@NonNull Throwable error);
    }

    private final TextRecognizer recognizer =
            TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /** Guards against callbacks firing after the host is gone. */
    private volatile boolean released;

    /**
     * Recognizes text in {@code bitmap}. The bitmap is not recycled and must stay
     * valid until the callback runs.
     */
    public void scan(@NonNull final Bitmap bitmap, @NonNull final Callback callback) {
        if (released) {
            return;
        }
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener(visionText -> compose(bitmap, visionText, callback))
                .addOnFailureListener(error -> dispatchFailure(callback, error));
    }

    private void compose(Bitmap bitmap, Text visionText, Callback callback) {
        worker.execute(() -> {
            if (released) {
                return;
            }
            final ScanResult result;
            final Bitmap overlay;
            try {
                result = flatten(bitmap, visionText);
                overlay = BoxPainter.paint(bitmap, result);
            } catch (RuntimeException | OutOfMemoryError error) {
                dispatchFailure(callback, error);
                return;
            }
            mainHandler.post(() -> {
                if (!released) {
                    callback.onScanned(result, overlay);
                }
            });
        });
    }

    private void dispatchFailure(Callback callback, Throwable error) {
        mainHandler.post(() -> {
            if (!released) {
                callback.onFailed(error);
            }
        });
    }

    private static ScanResult flatten(Bitmap source, Text visionText) {
        List<ScanBlock> blocks = new ArrayList<>();
        List<ScanLine> flattened = new ArrayList<>();
        int index = 0;

        for (Text.TextBlock block : visionText.getTextBlocks()) {
            List<ScanLine> blockLines = new ArrayList<>();
            Rect blockBox = toRect(block.getBoundingBox());

            for (Text.Line line : block.getLines()) {
                String content = line.getText();
                if (content == null || content.trim().isEmpty()) {
                    continue;
                }
                List<Rect> words = new ArrayList<>();
                for (Text.Element element : line.getElements()) {
                    Rect wordBox = toRect(element.getBoundingBox());
                    if (wordBox != null) {
                        words.add(wordBox);
                    }
                }
                index++;
                ScanLine scanLine = new ScanLine(index, content.trim(), toRect(line.getBoundingBox()), words);
                blockLines.add(scanLine);
                flattened.add(scanLine);
            }

            if (blockLines.isEmpty()) {
                continue;
            }
            blocks.add(new ScanBlock(blockBox != null ? blockBox : unionOf(blockLines), blockLines));
        }

        StringBuilder plain = new StringBuilder();
        for (ScanLine line : flattened) {
            if (plain.length() > 0) {
                plain.append('\n');
            }
            plain.append(line.text);
        }
        return new ScanResult(source, blocks, flattened, plain.toString());
    }

    private static Rect unionOf(List<ScanLine> lines) {
        Rect union = null;
        for (ScanLine line : lines) {
            if (!line.hasBounds()) {
                continue;
            }
            if (union == null) {
                union = new Rect(line.bounds);
            } else {
                union.union(line.bounds);
            }
        }
        return union != null ? union : new Rect();
    }

    private static Rect toRect(Rect source) {
        return source != null ? new Rect(source) : null;
    }

    /** Releases the recognizer and stops the worker. Safe to call twice. */
    public void close() {
        if (released) {
            return;
        }
        released = true;
        worker.shutdownNow();
        recognizer.close();
    }
}
