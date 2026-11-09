package com.shizi.ocr;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.shizi.ocr.img.ImageSource;
import com.shizi.ocr.ocr.ScanResult;
import com.shizi.ocr.ocr.TextScanner;
import com.shizi.ocr.util.UiKit;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private ImageView preview;
    private TextView hint;
    private TextView result;
    private Button scanButton;

    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private TextScanner scanner;
    private Bitmap loaded;
    private int loadGeneration;

    private final ActivityResultLauncher<String> pickImage =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    load(uri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        preview = findViewById(R.id.preview);
        hint = findViewById(R.id.hint);
        result = findViewById(R.id.result);
        scanButton = findViewById(R.id.btn_scan);

        findViewById(R.id.btn_pick).setOnClickListener(this);
        preview.setOnClickListener(this);
        scanButton.setOnClickListener(this);

        scanner = new TextScanner();
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.btn_scan) {
            scan();
        } else {
            pickImage.launch("image/*");
        }
    }

    private void load(final Uri uri) {
        final int generation = ++loadGeneration;
        scanButton.setEnabled(false);

        io.execute(() -> {
            Bitmap bitmap = null;
            try {
                bitmap = ImageSource.decode(this, uri);
            } catch (IOException | OutOfMemoryError ignored) {
            }
            final Bitmap decoded = bitmap;

            runOnUiThread(() -> {
                if (generation != loadGeneration || isFinishing()) {
                    return;
                }
                if (decoded == null) {
                    UiKit.toast(MainActivity.this, R.string.decode_failed);
                    return;
                }
                loaded = decoded;
                hint.setVisibility(View.GONE);
                preview.setImageBitmap(decoded);
                result.setText("");
                scanButton.setEnabled(true);
            });
        });
    }

    private void scan() {
        if (loaded == null) {
            return;
        }
        scanButton.setEnabled(false);
        scanButton.setText(R.string.scanning);

        scanner.scan(loaded, new TextScanner.Callback() {

            @Override
            public void onScanned(@NonNull ScanResult scanResult, @NonNull Bitmap overlay) {
                resetScanButton();
                preview.setImageBitmap(overlay);
                result.setText(scanResult.isEmpty()
                        ? getString(R.string.no_text)
                        : scanResult.text);
            }

            @Override
            public void onFailed(@NonNull Throwable error) {
                resetScanButton();
                UiKit.toast(MainActivity.this, R.string.scan_failed);
            }
        });
    }

    private void resetScanButton() {
        scanButton.setText(R.string.scan);
        scanButton.setEnabled(true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        loadGeneration++;
        if (scanner != null) {
            scanner.close();
            scanner = null;
        }
        io.shutdownNow();
    }
}
