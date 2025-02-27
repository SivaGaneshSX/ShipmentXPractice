package com.example.demo;

import static com.example.demo.OtpVerificationActivity.LOCATION_PERMISSION_REQUEST;

import android.Manifest;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.os.Build;
import android.os.Bundle;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.Task;


public class MainActivity extends AppCompatActivity {

    private static final String LOCAL_CHANNEL_ID = "local_channel";

    private static final int NOTIFICATION_REQUEST_CODE = 101;
    private static final int PHONE_STATE_PERMISSION_CODE = 103;
    private static final String PREFS_NAME = "UserDetails";
    private static final String OTP_KEY = "generatedOtp";

    private EditText nameInput, emailInput, phoneInput;
    private Button sendOtpButton, notifyButton;
    private ImageView openMapButton;
    private static final int GOOGLE_SIGN_IN_REQUEST_CODE = 1001;
    private GoogleSignInClient googleSignInClient;
    private Button googleSignInButton;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        googleSignInButton = findViewById(R.id.googleSignInButton);


        nameInput = findViewById(R.id.nameInput);
        emailInput = findViewById(R.id.emailInput);
        phoneInput = findViewById(R.id.phoneNumberInput);
        sendOtpButton = findViewById(R.id.sendOtpButton);
        notifyButton = findViewById(R.id.notifyButton);
        openMapButton = findViewById(R.id.openMapButton);
        createNotificationChannels();
        requestPermissions();

        // Auto-detect phone number when user enters the input field
        phoneInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                retrieveSimPhoneNumber();
            }
        });

        sendOtpButton.setOnClickListener(v -> checkSimAndSendOtp());
        notifyButton.setOnClickListener(v -> triggerLocalNotification());
        openMapButton.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, MapActivity.class)));
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .build();

        googleSignInClient = GoogleSignIn.getClient(this, gso);

        googleSignInButton.setOnClickListener(v -> signInWithGoogle());
    }
    private void signInWithGoogle() {
        Intent signInIntent = googleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, GOOGLE_SIGN_IN_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == GOOGLE_SIGN_IN_REQUEST_CODE) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                if (account != null) {
                    String name = account.getDisplayName();
                    String email = account.getEmail();

                    // Save user details if needed
                    SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putString("Name", name);
                    editor.putString("Email", email);
                    editor.apply();

                    Toast.makeText(this, "Signed in as: " + name, Toast.LENGTH_SHORT).show();

                    // Fetch location before redirecting
                    fetchLocationAndRedirect(name);
                }
            } catch (ApiException e) {
                Toast.makeText(this, "Sign-in failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void fetchLocationAndRedirect(String username) {
        FusedLocationProviderClient fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST);
            return;
        }

        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            double latitude = 0.0, longitude = 0.0;
            String userLocation = "Fetching location...";

            if (location != null) {
                latitude = location.getLatitude();
                longitude = location.getLongitude();
                userLocation = getAddressFromLocation(latitude, longitude);
            }

            // Redirect to SuccessPageActivity
            Intent intent = new Intent(this, SuccessPageActivity.class);
            intent.putExtra("USERNAME", username);
            intent.putExtra("LOCATION", userLocation);
            intent.putExtra("LATITUDE", latitude);
            intent.putExtra("LONGITUDE", longitude);
            startActivity(intent);
            finish();
        });
    }

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


    private void signOut() {
        googleSignInClient.signOut().addOnCompleteListener(this, task ->
                Toast.makeText(this, "Signed out", Toast.LENGTH_SHORT).show());
    }

    private void retrieveSimPhoneNumber() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_NUMBERS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.READ_PHONE_NUMBERS
            }, PHONE_STATE_PERMISSION_CODE);
            return;
        }

        SubscriptionManager subscriptionManager = (SubscriptionManager) getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        if (subscriptionManager != null) {
            List<SubscriptionInfo> subscriptionInfoList = subscriptionManager.getActiveSubscriptionInfoList();
            if (subscriptionInfoList != null && !subscriptionInfoList.isEmpty()) {
                List<String> phoneNumbers = new ArrayList<>();
                for (SubscriptionInfo info : subscriptionInfoList) {
                    String phoneNumber = info.getNumber();
                    if (phoneNumber != null && !phoneNumber.isEmpty()) {
                        phoneNumbers.add(phoneNumber);
                    }
                }
                if (!phoneNumbers.isEmpty()) {

                    showPhoneNumberSelectionDialog(phoneNumbers);
                } else {
                    Toast.makeText(this, "No phone numbers available from SIM cards.", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "No active SIM cards detected.", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "Unable to access SIM information.", Toast.LENGTH_SHORT).show();
        }
    }
    private void showPhoneNumberSelectionDialog(List<String> phoneNumbers) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Phone Number");

        // Add "Use another number" option
        phoneNumbers.add("Use another number");

        String[] phoneArray = phoneNumbers.toArray(new String[0]);

        builder.setItems(phoneArray, (dialog, which) -> {
            if (which == phoneNumbers.size() - 1) {
                // User chose "Use another number"
                phoneInput.setText("");  // Clear the EditText field for manual input
                phoneInput.setEnabled(true); // Make sure it's editable
                phoneInput.requestFocus();
            } else {
                // User selected a number from SIM
                phoneInput.setText(phoneArray[which]);
                phoneInput.setEnabled(false); // Disable input if SIM number is selected
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        builder.show();
    }

//    private void showManualNumberInputDialog() {
//        AlertDialog.Builder builder = new AlertDialog.Builder(this);
//        builder.setTitle("Enter Phone Number");
//
//        final EditText input = new EditText(this);
//        input.setHint("Enter phone number");
//        builder.setView(input);
//
//        builder.setPositiveButton("OK", (dialog, which) -> {
//            String enteredNumber = input.getText().toString().trim();
//            if (!enteredNumber.isEmpty() && enteredNumber.length() >= 10) {
//                phoneInput.setText(enteredNumber);
//            } else {
//                Toast.makeText(this, "Please enter a valid phone number", Toast.LENGTH_SHORT).show();
//            }
//        });
//
//        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
//        builder.show();
//    }



    private void checkSimAndSendOtp() {
        SubscriptionManager subscriptionManager = (SubscriptionManager) getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        if (subscriptionManager != null) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_PHONE_STATE}, PHONE_STATE_PERMISSION_CODE);
                return;
            }
        }

        sendOtp(null);
    }



    private void sendOtp(SubscriptionInfo selectedSim) {
        String name = nameInput.getText().toString().trim();
        String email = emailInput.getText().toString().trim();
        String phoneNumber = phoneInput.getText().toString().trim();

        if (name.isEmpty() || email.isEmpty() || phoneNumber.isEmpty() || phoneNumber.length() < 10) {
            Toast.makeText(this, "Enter valid details before selecting OTP", Toast.LENGTH_SHORT).show();
            return;
        }

        String generatedOtp = String.format("%06d", new Random().nextInt(999999));

        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("Name", name);
        editor.putString("Email", email);
        editor.putString("Phone", phoneNumber);
        editor.putString(OTP_KEY, generatedOtp);
        editor.apply();

        sendOtpNotification(generatedOtp);

        Intent intent = new Intent(this, OtpVerificationActivity.class);
        intent.putExtra(OTP_KEY, generatedOtp);
        startActivity(intent);
    }

    private void sendOtpNotification(String otp) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, LOCAL_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Your OTP Code")
                .setContentText("Use this OTP: " + otp)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        NotificationManagerCompat manager = NotificationManagerCompat.from(this);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            manager.notify((int) System.currentTimeMillis(), builder.build());
        } else {
            Toast.makeText(this, "Notification permission not granted", Toast.LENGTH_SHORT).show();
        }
    }

    private void triggerLocalNotification() {
        Intent intent = new Intent(this, LocationActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, LOCAL_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Access Your Location")
                .setContentText("Click to open the location activity.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        NotificationManagerCompat manager = NotificationManagerCompat.from(this);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            manager.notify((int) System.currentTimeMillis(), builder.build());
        } else {
            Toast.makeText(this, "Notification permission not granted", Toast.LENGTH_SHORT).show();
        }
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel localChannel = new NotificationChannel(
                    LOCAL_CHANNEL_ID, "Local Notifications", NotificationManager.IMPORTANCE_HIGH);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(localChannel);
            }
        }
    }

    private void requestPermissions() {
        List<String> permissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.READ_PHONE_STATE);
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_NUMBERS) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.READ_PHONE_NUMBERS);
        }

        if (!permissions.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toArray(new String[0]), PHONE_STATE_PERMISSION_CODE);
        }
    }
}
