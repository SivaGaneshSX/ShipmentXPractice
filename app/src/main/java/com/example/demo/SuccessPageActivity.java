package com.example.demo;

import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.fragment.app.FragmentActivity;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class SuccessPageActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private EditText destinationInput;
    private Button showRouteButton;
    private double currentLat, currentLng;
    private String apiKey;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_success_page);

        apiKey = getString(R.string.google_maps_key); // Fetch API Key from strings.xml

        destinationInput = findViewById(R.id.destinationInput);
        showRouteButton = findViewById(R.id.showRouteButton);

        // Get current location from Intent
        currentLat = getIntent().getDoubleExtra("LATITUDE", 0.0);
        currentLng = getIntent().getDoubleExtra("LONGITUDE", 0.0);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        showRouteButton.setOnClickListener(v -> {
            String destination = destinationInput.getText().toString().trim();
            if (destination.isEmpty()) {
                Toast.makeText(this, "Please enter a destination", Toast.LENGTH_SHORT).show();
                return;
            }

            double[] destCoordinates = getLocationFromAddress(destination);
            if (destCoordinates == null) {
                Toast.makeText(this, "Invalid destination address", Toast.LENGTH_SHORT).show();
                return;
            }

            drawRoute(destCoordinates[0], destCoordinates[1]);
        });
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        LatLng currentLocation = new LatLng(currentLat, currentLng);
        mMap.addMarker(new MarkerOptions().position(currentLocation).title("Current Location"));
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 12));
    }

    private double[] getLocationFromAddress(String location) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocationName(location, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                return new double[]{address.getLatitude(), address.getLongitude()};
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void drawRoute(double destLat, double destLng) {
        LatLng source = new LatLng(currentLat, currentLng);
        LatLng destination = new LatLng(destLat, destLng);

        mMap.clear();
        mMap.addMarker(new MarkerOptions().position(source).title("Current Location")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));
        mMap.addMarker(new MarkerOptions().position(destination).title("Destination"));

        getRoute(source, destination);
    }

    private void getRoute(LatLng origin, LatLng dest) {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://maps.googleapis.com/maps/api/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        GoogleMapsService service = retrofit.create(GoogleMapsService.class);
        Call<JsonObject> call = service.getDirections(
                origin.latitude + "," + origin.longitude,
                dest.latitude + "," + dest.longitude,
                "driving",
                apiKey
        );

        call.enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if (response.isSuccessful() && response.body() != null) {
                    parseAndDrawRoute(response.body());
                } else {
                    Toast.makeText(SuccessPageActivity.this, "Failed to get route", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                Toast.makeText(SuccessPageActivity.this, "Network error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void parseAndDrawRoute(JsonObject jsonData) {
        try {
            JsonObject route = jsonData.getAsJsonArray("routes").get(0).getAsJsonObject();
            String polyline = route.getAsJsonObject("overview_polyline").get("points").getAsString();
            List<LatLng> points = decodePoly(polyline);

            PolylineOptions polylineOptions = new PolylineOptions().addAll(points).width(12).color(Color.BLUE);
            mMap.addPolyline(polylineOptions);
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(currentLat, currentLng), 12));
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error parsing route", Toast.LENGTH_SHORT).show();
        }
    }

    private List<LatLng> decodePoly(String encoded) {
        List<LatLng> poly = new ArrayList<>();
        int index = 0, len = encoded.length();
        int lat = 0, lng = 0;

        while (index < len) {
            int b, shift = 0, result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;

            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;

            poly.add(new LatLng((lat / 1E5), (lng / 1E5)));
        }
        return poly;
    }
}
