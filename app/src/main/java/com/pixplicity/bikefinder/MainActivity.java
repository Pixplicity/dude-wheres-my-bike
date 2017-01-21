package com.pixplicity.bikefinder;

import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;

import android.util.Log;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import java.util.ArrayList;

public class MainActivity extends FragmentActivity implements
        OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private static final int RC_LOCATION = 1001;

    private static final LatLng DEFAULT_LOCATION = new LatLng(52.3745291, 4.7585319);

    private static final String TAG = MainActivity.class.getSimpleName();
    private GoogleMap mMap;
    private DatabaseReference mDatabaseReference;
    private FirebaseDatabase mFirebaseDatabase;
    private ArrayList<Bike> mBikes;
    private ChildEventListener mChildEventListener;

    private GoogleApiClient mGoogleApiClient;
    private Location mLastLocation;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        mGoogleApiClient = new GoogleApiClient.Builder(this)
            .addConnectionCallbacks(this)
            .addOnConnectionFailedListener(this)
            .addApi(LocationServices.API)
            .build();

        // By enabling persistence, any data that we sync while online will be
        // persisted to disk and available offline, even when we restart the app.
        if (mFirebaseDatabase == null) {
            mFirebaseDatabase = FirebaseDatabase.getInstance();
            FirebaseDatabase.getInstance().setPersistenceEnabled(true);
        }
        mDatabaseReference = mFirebaseDatabase.getReference();

        mBikes = new ArrayList<>();

        // for testing only
        Bike testBike = new Bike();
        testBike.setUuid();
        testBike.setTitle("Blah blah blah");
        testBike.setLocationLatitude(DEFAULT_LOCATION.latitude);
        testBike.setLocationLongitude(DEFAULT_LOCATION.longitude);
        mDatabaseReference.child(testBike.getUuid()).setValue(testBike);

        mChildEventListener = new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                Bike bike = dataSnapshot.getValue(Bike.class);
                mBikes.add(bike);
                Log.v(TAG, "child added" + bike.getUuid());
                Log.v(TAG, "mbikes size" + mBikes.size());
            }

            @Override
            public void onChildChanged(DataSnapshot dataSnapshot, String s) {
                for(Bike bike : mBikes){
                    if(dataSnapshot.getKey().equals(bike.getUuid())){
                        mBikes.remove(bike);
                        Bike newBike = dataSnapshot.getValue(Bike.class);
                        mBikes.add(newBike);
                        Log.v(TAG, "child updated");
                    }
                }
            }

            @Override
            public void onChildRemoved(DataSnapshot dataSnapshot) {
                for(Bike bike : mBikes){
                    if(dataSnapshot.getKey().equals(bike.getUuid())){
                        mBikes.remove(bike);
                        Log.v(TAG, "child removed");
                    }
                }
            }

            @Override
            public void onChildMoved(DataSnapshot dataSnapshot, String s) {

            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        };
        mDatabaseReference.addChildEventListener(mChildEventListener);
    }

    protected void onStart() {
        mGoogleApiClient.connect();
        super.onStart();
    }

    protected void onStop() {
        mGoogleApiClient.disconnect();
        super.onStop();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case RC_LOCATION:
                onLocationPermitted(false);
                break;
        }
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        onLocationPermitted(true);
        mMap.moveCamera(CameraUpdateFactory.newLatLng(DEFAULT_LOCATION));

        mMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
            @Override
            public void onMapClick(LatLng latLng) {
                addMarker(latLng, null, true);
            }
        });

        mMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(Marker marker) {
                removeMarker(marker);
                return false;
            }
        });

        initializeMarkers();
    }

    private void onLocationPermitted(boolean request) {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            if (request) {
                ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, RC_LOCATION);
            }
        } else {
            mMap.setMyLocationEnabled(true);
            boolean centerOnLocation = mLastLocation == null;
            mLastLocation = LocationServices.FusedLocationApi.getLastLocation(
                    mGoogleApiClient);
            if (mLastLocation != null && centerOnLocation) {
                LatLng latLng = new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude());
                CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(latLng, 15f);
                mMap.animateCamera(cameraUpdate);
            }
        }
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        onLocationPermitted(false);
    }

    @Override
    public void onConnectionSuspended(int i) {
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
    }

    private void initializeMarkers() {
        // TODO fetch from Firebase

        //addMarker(latLng, title, false);
    }

    private void addMarker(LatLng latLng, String title, boolean animateTo) {
        mMap.addMarker(new MarkerOptions().position(latLng).title(title));
        CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLng(latLng);
        if (animateTo) {
            mMap.animateCamera(cameraUpdate);
        } else {
            mMap.moveCamera(cameraUpdate);
        }

        // TODO inform Firebase
    }

    private void removeMarker(Marker marker) {
        marker.remove();

        // TODO inform Firebase
    }
}
