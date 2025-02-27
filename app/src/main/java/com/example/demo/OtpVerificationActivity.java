package com.example.demo;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class OtpVerificationActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "UserDetails";
    private static final String OTP_KEY = "generatedOtp";
    public static final int LOCATION_PERMISSION_REQUEST = 1;
    private static final String CHANNEL_ID = "OTP_CHANNEL";

    private EditText otpInput;
    private Button verifyOtpButton;
    private FusedLocationProviderClient fusedLocationClient;
    private String userLocation = "Fetching location...";
    private boolean isLocationFetched = false;
    private double latitude, longitude;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_otp_verification);

        otpInput = findViewById(R.id.otpInput);
        verifyOtpButton = findViewById(R.id.verifyOtpButton);

        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String storedOtp = sharedPreferences.getString(OTP_KEY, "");
        String username = sharedPreferences.getString("Name", "User");

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        requestLocation();
        sendOtpNotification(storedOtp);

        verifyOtpButton.setOnClickListener(v -> {
            String enteredOtp = otpInput.getText().toString().trim();

            if (enteredOtp.isEmpty()) {
                Toast.makeText(this, "Please enter the OTP", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!enteredOtp.matches("\\d{4,6}")) {
                Toast.makeText(this, "Invalid OTP format", Toast.LENGTH_SHORT).show();
                return;
            }

            if (enteredOtp.equals(storedOtp)) {
                if (!isLocationFetched) {
                    Toast.makeText(this, "Fetching location, please wait...", Toast.LENGTH_SHORT).show();
                    return;
                }

                Toast.makeText(this, "OTP Verified Successfully!", Toast.LENGTH_SHORT).show();

                Intent intent = new Intent(OtpVerificationActivity.this, SuccessPageActivity.class);
                intent.putExtra("USERNAME", username);
                intent.putExtra("LOCATION", userLocation);
                intent.putExtra("LATITUDE", latitude);
                intent.putExtra("LONGITUDE", longitude);
                startActivity(intent);
                finish();
            } else {
                Toast.makeText(this, "Invalid OTP", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void sendOtpNotification(String otp) {
        createNotificationChannel();

        Intent intent = new Intent(this, OtpVerificationActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Your OTP Code")
                .setContentText("Use this OTP to verify: " + otp)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.notify(1, builder.build());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "OTP Notifications", NotificationManager.IMPORTANCE_HIGH);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private void requestLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST);
            return;
        }

        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                latitude = location.getLatitude();
                longitude = location.getLongitude();
                userLocation = getAddressFromLocation(latitude, longitude);
                isLocationFetched = true;
            } else {
                requestLocationUpdates();
            }
        });
    }

    private void requestLocationUpdates() {
        LocationRequest locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(1000)
                .setFastestInterval(500);

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null);
    }

    private final LocationCallback locationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(LocationResult locationResult) {
            if (locationResult == null) return;

            for (Location location : locationResult.getLocations()) {
                if (!isLocationFetched) {
                    latitude = location.getLatitude();
                    longitude = location.getLongitude();
                    userLocation = getAddressFromLocation(latitude, longitude);

                    // Save coordinates to intent
                    getIntent().putExtra("LATITUDE", latitude);
                    getIntent().putExtra("LONGITUDE", longitude);

                    isLocationFetched = true;
                    fusedLocationClient.removeLocationUpdates(this);
                }
            }
        }
    };

    private String getAddressFromLocation(double latitude, double longitude) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                return address.getAddressLine(0) + "\nCity: " + address.getLocality() +
                        "\nState: " + address.getAdminArea() +
                        "\nCountry: " + address.getCountryName() +
                        "\nPostal Code: " + address.getPostalCode();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "Fetching location...";
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                requestLocation();
            } else {
                Toast.makeText(this, "Location Permission Denied", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
