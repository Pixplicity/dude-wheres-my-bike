package com.pixplicity.bikefinder;

import android.os.Bundle;
import android.support.v4.app.FragmentActivity;

import android.util.Log;
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

public class MainActivity extends FragmentActivity implements OnMapReadyCallback {

    private static final String TAG = MainActivity.class.getSimpleName();
    private GoogleMap mMap;
    private DatabaseReference mDatabaseReference;
    private FirebaseDatabase mFirebaseDatabase;
    private ArrayList<Bike> mBikes;
    private ChildEventListener mChildEventListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

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
        mDatabaseReference.child(testBike.getUuid()).setValue(testBike);

        mChildEventListener = new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                Bike bike = dataSnapshot.getValue(Bike.class);
                mBikes.add(bike);
                Log.v(TAG, "child added");
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

    private void initializeMarkers() {
        // TODO fetch from Firebase

        // Add a marker in Sydney and move the camera
        LatLng latLng = new LatLng(-34, 151);
        String title = "Marker in Sydney";
        addMarker(latLng, title, false);
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

    @Override
    protected void onResume() {
        super.onResume();
        mDatabaseReference.addChildEventListener(mChildEventListener);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mDatabaseReference.removeEventListener(mChildEventListener);
    }
}
