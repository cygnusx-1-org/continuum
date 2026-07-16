package ml.docilealligator.infinityforreddit.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import com.google.zxing.ResultPoint;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;
import java.util.List;
import java.util.Objects;
import ml.docilealligator.infinityforreddit.R;

public class QRCodeScannerActivity extends AppCompatActivity {
    public static final String EXTRA_QR_CODE_RESULT = "EQCR";
    private DecoratedBarcodeView barcodeView;
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    // Permission granted, initialize the scanner
                    initializeScanner();
                } else {
                    // Permission denied
                    Toast.makeText(this, R.string.camera_permission_required, Toast.LENGTH_SHORT).show();
                    setResult(RESULT_CANCELED);
                    finish();
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // No flags that would clear the activity stack
        setContentView(R.layout.activity_qrcode_scanner);

        Toolbar toolbar = findViewById(R.id.toolbar_qrcode_scanner_activity);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        }
        setTitle(R.string.scan_qr_code);

        barcodeView = findViewById(R.id.barcode_scanner);

        // Check camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            // Permission already granted, initialize scanner
            initializeScanner();
        } else {
            // Request permission
            requestPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void initializeScanner() {
        barcodeView.decodeContinuous(new BarcodeCallback() {
            @Override
            public void barcodeResult(BarcodeResult result) {
                if (result.getText() != null) {
                    // Create result intent and finish properly
                    Intent resultIntent = new Intent();
                    resultIntent.putExtra(EXTRA_QR_CODE_RESULT, result.getText());
                    setResult(RESULT_OK, resultIntent);
                    finish();
                }
            }

            @Override
            public void possibleResultPoints(List<ResultPoint> resultPoints) {
                // Not used
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        barcodeView.resume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        barcodeView.pause();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            // Ensure we set a result when user presses back
            setResult(RESULT_CANCELED);
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        // Ensure we set a result when user presses back button
        setResult(RESULT_CANCELED);
        super.onBackPressed();
    }
}