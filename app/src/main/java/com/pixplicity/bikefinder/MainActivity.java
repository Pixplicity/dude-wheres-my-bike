package com.pixplicity.bikefinder;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.widget.Toast;

import com.facebook.AccessToken;
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.FacebookSdk;
import com.facebook.login.LoginResult;
import com.facebook.login.widget.LoginButton;
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
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FacebookAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.pixplicity.bikefinder.utils.DatabaseUtils;

import java.util.ArrayList;

public class MainActivity extends FragmentActivity implements
        OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private static final String TAG = MainActivity.class.getSimpleName();

    private static final int RC_LOCATION = 1001;

    private static final LatLng DEFAULT_LOCATION = new LatLng(52.3745291, 4.7585319);

    private GoogleMap mMap;
    private Location mLastLocation;

    private DatabaseReference mDatabaseReference;
    private FirebaseDatabase mFirebaseDatabase;
    private ChildEventListener mChildEventListener;
    private FirebaseAuth mAuth;
    private FirebaseAuth.AuthStateListener mAuthListener;

    private GoogleApiClient mGoogleApiClient;
    private CallbackManager mCallbackManager;

    private ArrayList<Bike> mBikes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Must be called before inflating the layout with the LoginButton
        FacebookSdk.sdkInitialize(this);

        setContentView(R.layout.activity_main);

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        // Initialize Facebook Login button
        mCallbackManager = CallbackManager.Factory.create();
        LoginButton loginButton = (LoginButton) findViewById(R.id.button_facebook_login);
        loginButton.setReadPermissions("email", "public_profile");
        loginButton.registerCallback(mCallbackManager, new FacebookCallback<LoginResult>() {
            @Override
            public void onSuccess(LoginResult loginResult) {
                Log.d(TAG, "facebook:onSuccess:" + loginResult);
                handleFacebookAccessToken(loginResult.getAccessToken());
            }

            @Override
            public void onCancel() {
                Log.d(TAG, "facebook:onCancel");
                // ...
            }

            @Override
            public void onError(FacebookException error) {
                Log.d(TAG, "facebook:onError", error);
                // ...
            }
        });

        mGoogleApiClient = new GoogleApiClient.Builder(this)
            .addConnectionCallbacks(this)
            .addOnConnectionFailedListener(this)
            .addApi(LocationServices.API)
            .build();

        mFirebaseDatabase = DatabaseUtils.getFirebaseDatabase();
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

        mAuth = FirebaseAuth.getInstance();
        mAuthListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser user = firebaseAuth.getCurrentUser();
                if (user != null) {
                    // User is signed in
                    Log.d(TAG, "onAuthStateChanged:signed_in:" + user.getUid());
                } else {
                    // User is signed out
                    Log.d(TAG, "onAuthStateChanged:signed_out");
                }
                // ...
            }
        };
    }

    protected void onStart() {
        super.onStart();
        mGoogleApiClient.connect();
        mAuth.addAuthStateListener(mAuthListener);
    }

    protected void onStop() {
        super.onStop();
        if (mGoogleApiClient != null) {
            mGoogleApiClient.disconnect();
        }
        if (mAuthListener != null) {
            mAuth.removeAuthStateListener(mAuthListener);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case RC_LOCATION:
                onLocationPermitted(false);
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Pass the activity result back to the Facebook SDK
        mCallbackManager.onActivityResult(requestCode, resultCode, data);
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

    private void handleFacebookAccessToken(AccessToken token) {
        Log.d(TAG, "handleFacebookAccessToken:" + token);

        AuthCredential credential = FacebookAuthProvider.getCredential(token.getToken());
        mAuth.signInWithCredential(credential)
             .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                 @Override
                 public void onComplete(@NonNull Task<AuthResult> task) {
                     Log.d(TAG, "signInWithCredential:onComplete:" + task.isSuccessful());

                     // If sign in fails, display a message to the user. If sign in succeeds
                     // the auth state listener will be notified and logic to handle the
                     // signed in user can be handled in the listener.
                     if (!task.isSuccessful()) {
                         Log.w(TAG, "signInWithCredential", task.getException());
                         Toast.makeText(MainActivity.this, "Authentication failed.",
                                 Toast.LENGTH_SHORT).show();
                     }
                 }
             });
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
