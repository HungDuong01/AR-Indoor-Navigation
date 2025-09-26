package com.example.indoornavigation;

import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private Button scanButton;
    private TextView wifiDisplay;
    private ScrollView scrollView;
    private WifiManager wifiManager;
    private DBHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        if (ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[] { android.Manifest.permission.ACCESS_FINE_LOCATION },
                    1);
        }

        scanButton = findViewById(R.id.btnScan);
        wifiDisplay = findViewById(R.id.wifiDisplay);
        scrollView = findViewById(R.id.scrollView);

        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        dbHelper = new DBHelper(this);

        scanButton.setOnClickListener(v -> scanAndDisplayTop5WiFi());
    }

    private void scanAndDisplayTop5WiFi() {
        if (ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[] { android.Manifest.permission.ACCESS_FINE_LOCATION },
                    1);
            return;
        }

        boolean success = wifiManager.startScan();
        if (!success) {
            Toast.makeText(this, "Wi-Fi scan failed. Try again.", Toast.LENGTH_SHORT).show();
            return;
        }

        List<ScanResult> results = wifiManager.getScanResults();
        if (results.isEmpty()) {
            wifiDisplay.setText("No Wi-Fi networks detected.");
            return;
        }

        // Sort the results by signal strength (RSSI) in descending order
        Collections.sort(results, Comparator.comparingInt(result -> -result.level));

        StringBuilder wifiInfo = new StringBuilder("Top 5 Nearby Access Points:\n");
        for (int i = 0; i < Math.min(5, results.size()); i++) {
            ScanResult result = results.get(i);
            double estimatedDistance = calculateDistance(result.level);
            wifiInfo.append(i + 1).append(". SSID: ").append(result.SSID)
                    .append(" | BSSID: ").append(result.BSSID)
                    .append(" | RSSI: ").append(result.level)
                    .append(" | Estimated Distance: ").append(String.format("%.2f", estimatedDistance))
                    .append(" meters\n");
        }
        wifiDisplay.setText(wifiInfo.toString());
    }

    private double calculateDistance(int rssi) {
        int txPower = -50; // Adjust based on real measurements of your AP
        double pathLossExponent = 3.0; // Adjust for indoor environments (ranges from 2.5 to 4)

        return Math.pow(10, (txPower - rssi) / (10 * pathLossExponent));
    }
}
