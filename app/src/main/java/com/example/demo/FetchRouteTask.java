package com.example.demo;

import android.content.Context;
import android.graphics.Color;
import android.os.AsyncTask;
import android.widget.Toast;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.PolylineOptions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class FetchRouteTask extends AsyncTask<String, Void, List<LatLng>> {
    private GoogleMap mMap;
    private Context context;

    public FetchRouteTask(GoogleMap mMap, Context context) {
        this.mMap = mMap;
        this.context = context;
    }

    @Override
    protected List<LatLng> doInBackground(String... urls) {
        try {
            String response = getResponseFromUrl(urls[0]);
            return parseRoute(response);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    protected void onPostExecute(List<LatLng> route) {
        if (route != null && mMap != null) {
            mMap.addPolyline(new PolylineOptions().addAll(route).width(10).color(Color.BLUE));
        } else {
            Toast.makeText(context, "No route found", Toast.LENGTH_SHORT).show();
        }
    }

    private String getResponseFromUrl(String requestUrl) throws Exception {
        URL url = new URL(requestUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();
        return response.toString();
    }

    private List<LatLng> parseRoute(String jsonResponse) {
        List<LatLng> route = new ArrayList<>();
        try {
            JSONObject jsonObject = new JSONObject(jsonResponse);
            JSONArray routes = jsonObject.getJSONArray("routes");
            if (routes.length() > 0) {
                JSONArray steps = routes.getJSONObject(0).getJSONArray("legs").getJSONObject(0).getJSONArray("steps");
                for (int i = 0; i < steps.length(); i++) {
                    JSONObject step = steps.getJSONObject(i);
                    JSONObject startLocation = step.getJSONObject("start_location");
                    route.add(new LatLng(startLocation.getDouble("lat"), startLocation.getDouble("lng")));

                    JSONObject endLocation = step.getJSONObject("end_location");
                    route.add(new LatLng(endLocation.getDouble("lat"), endLocation.getDouble("lng")));
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return route;
    }
}
