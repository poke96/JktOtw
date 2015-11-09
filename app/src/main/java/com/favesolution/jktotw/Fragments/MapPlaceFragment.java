package com.favesolution.jktotw.Fragments;

import android.content.res.TypedArray;
import android.location.Location;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;

import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.favesolution.jktotw.Activities.DetailPlaceActivity;
import com.favesolution.jktotw.Models.Place;
import com.favesolution.jktotw.NetworkUtils.CustomJsonRequest;
import com.favesolution.jktotw.NetworkUtils.RequestQueueSingleton;
import com.favesolution.jktotw.NetworkUtils.UrlEndpoint;
import com.favesolution.jktotw.R;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * A placeholder fragment containing a simple view.
 */
public class MapPlaceFragment extends SupportMapFragment implements GoogleApiClient.ConnectionCallbacks {
    private static final String ARG_POSITION = "arg_position";
    private List<Place> mPlaces;
    private List<Marker> mMarkers = new ArrayList<>();
    private String mCategoryFilter;
    private GoogleApiClient mClient;
    private Location mCurrentLocation;
    private int mPosition;
    private Marker activeMarker;
    private int activeMarkerPosition;
    private GoogleMap mMap;
    public static MapPlaceFragment newInstance(int position) {
        Bundle args = new Bundle();
        args.putInt(ARG_POSITION, position);
        MapPlaceFragment fragment = new MapPlaceFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        mPosition = getArguments().getInt(ARG_POSITION);
        ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        actionBar.setTitle(getResources()
                .obtainTypedArray(R.array.categories)
                .getString(mPosition) + " " + getString(R.string.near_you));
        TypedArray categoryFilterList = getResources().obtainTypedArray(R.array.category_filter);
        mCategoryFilter = categoryFilterList.getString(mPosition);
        mClient = new GoogleApiClient.Builder(getActivity())
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .build();
        getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(GoogleMap googleMap) {
                mMap = googleMap;
                mMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
                    @Override
                    public boolean onMarkerClick(Marker marker) {
                        if (marker.equals(activeMarker)) {
                            Place place = mPlaces.get(activeMarkerPosition);
                            startActivity(DetailPlaceActivity.newIntent(getActivity(),place.getId(),place.getName()));
                        }
                        for (int i = 0; i < mMarkers.size(); i++) {
                            if (marker.equals(mMarkers.get(i))) {
                                activeMarker = marker;
                                activeMarkerPosition = i;
                                break;
                            }
                        }
                        return false;
                    }
                });
                updateMap();
            }
        });
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                getActivity().onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
    @Override
    public void onStart() {
        super.onStart();
        mClient.connect();
    }

    @Override
    public void onStop() {
        super.onStop();
        mClient.disconnect();
        RequestQueueSingleton.getInstance(getActivity()).getRequestQueue().cancelAll(this);
    }
    @Override
    public void onConnected(Bundle bundle) {
        LocationRequest request = LocationRequest.create();
        request.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        request.setNumUpdates(1);
        request.setInterval(0);
        LocationServices.FusedLocationApi
                .requestLocationUpdates(mClient, request, new LocationListener() {
                    @Override
                    public void onLocationChanged(Location location) {
                        if (mCurrentLocation == null) {
                            mCurrentLocation = location;
                            refreshPlace();
                        } else {
                            mCurrentLocation = location;
                        }
                        updateMap();
                    }
                });
    }

    @Override
    public void onConnectionSuspended(int i) {

    }
    private void updateMap() {
        if(mMap == null || mCurrentLocation == null)
            return;
        LatLng latLngUser = new LatLng(mCurrentLocation.getLatitude(),mCurrentLocation.getLongitude());
        mMap.addMarker(new MarkerOptions().position(latLngUser).title(getString(R.string.you)));
        CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(latLngUser, 15);
        mMap.animateCamera(cameraUpdate);
        if(mPlaces==null)
            return;
        TypedArray categoryIcon = getResources().obtainTypedArray(R.array.category_icon_marker);
        BitmapDescriptor customMarker = BitmapDescriptorFactory
        .fromResource(categoryIcon.getResourceId(mPosition,0));
        for (Place place:mPlaces) {
            MarkerOptions marker = new MarkerOptions().position(place.getLatLng())
                    .title(place.getName()).icon(customMarker).snippet(place.getAddress());
            Marker m = mMap.addMarker(marker);
            mMarkers.add(m);
        }
    }
    private void refreshPlace() {
        RequestQueueSingleton.getInstance(getActivity())
                .getRequestQueue()
                .cancelAll(this);
        final String url = UrlEndpoint.searchNearbyPlace(mCurrentLocation, mCategoryFilter);
        CustomJsonRequest placeRequest = new CustomJsonRequest(url, null, new Response.Listener<JSONObject>() {
            @Override
            public void onResponse(JSONObject response) {
                try {
                    JSONArray result = response.getJSONArray("results");
                    mPlaces = Place.fromJson(result,mCurrentLocation);
                    updateMap();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Toast.makeText(getActivity(), "Network error", Toast.LENGTH_SHORT).show();
                Log.e("error", error.getMessage());
            }
        });
        placeRequest.setTag(this);
        RequestQueueSingleton.getInstance(getActivity())
                .addToRequestQueue(placeRequest);
    }

}
