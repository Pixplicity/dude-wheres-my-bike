package com.pixplicity.bikefinder;

import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.facebook.AccessToken;
import com.facebook.AccessTokenTracker;
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.FacebookSdk;
import com.facebook.login.LoginResult;
import com.facebook.login.widget.LoginButton;
import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.SignInButton;
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
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.pixplicity.bikefinder.utils.DatabaseUtils;
import com.pixplicity.easyprefs.library.Prefs;

import java.util.HashMap;
import java.util.UUID;

public class MainActivity extends FragmentActivity implements
        OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

    private static final String TAG = MainActivity.class.getSimpleName();

    private static final int RC_LOCATION = 1001;
    private static final int RC_SIGN_IN = 1002;

    private static final LatLng DEFAULT_LOCATION = new LatLng(52.3745291, 4.7585319);
    private static final String USER_ID = "user_id";

    private GoogleMap mMap;
    private Location mLastLocation;

    private DatabaseReference mDatabaseReference;
    private DatabaseReference mUserReference, mBikesReference;
    private FirebaseDatabase mFirebaseDatabase;
    private ChildEventListener mChildEventListener;
    private FirebaseAuth mAuth;
    private FirebaseAuth.AuthStateListener mAuthListener;

    private GoogleApiClient mGoogleApiClient;
    private CallbackManager mCallbackManager;
    private AccessTokenTracker mAccessTokenTracker;

    private final HashMap<String, Bike> mBikes = new HashMap<>();
    private final HashMap<String, Marker> mMarkers = new HashMap<>();
    private User mUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Must be called before inflating the layout with the LoginButton
        FacebookSdk.sdkInitialize(this);

        mAccessTokenTracker = new AccessTokenTracker() {
            @Override
            protected void onCurrentAccessTokenChanged(AccessToken oldAccessToken, AccessToken currentAccessToken) {
                if (currentAccessToken == null) {
                    FirebaseAuth.getInstance().signOut();
                }
            }
        };
        mAccessTokenTracker.startTracking();

        setContentView(R.layout.activity_main);

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        // Set the dimensions of the sign-in button.
        SignInButton signInButton = (SignInButton) findViewById(R.id.sign_in_button);
        signInButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent signInIntent = Auth.GoogleSignInApi.getSignInIntent(mGoogleApiClient);
                startActivityForResult(signInIntent, RC_SIGN_IN);
            }
        });

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

        // Configure sign-in to request the user's ID, email address, and basic
        // profile. ID and basic profile are included in DEFAULT_SIGN_IN.
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .enableAutoManage(this, this)
                .addApi(LocationServices.API)
                .addApi(Auth.GOOGLE_SIGN_IN_API, gso)
                .build();

        // Initialize the Prefs class
        new Prefs.Builder()
                .setContext(this)
                .setMode(ContextWrapper.MODE_PRIVATE)
                .setPrefsName(getPackageName())
                .setUseDefaultSharedPreference(true)
                .build();

        // Check if it's the first time the user uses the app
        mUser = new User();
        String userId = getOfflineUserId();
        mUser.setUserId(userId);

        mFirebaseDatabase = DatabaseUtils.getFirebaseDatabase();
        mDatabaseReference = mFirebaseDatabase.getReference();
        mUserReference = mDatabaseReference.child("users").child(mUser.getUserId());
        mBikesReference = mUserReference.child("bikes");

        mChildEventListener = new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                synchronized (mBikes) {
                    Bike bike = dataSnapshot.getValue(Bike.class);
                    addBike(bike, false);
                }
            }

            @Override
            public void onChildChanged(DataSnapshot dataSnapshot, String s) {
                synchronized (mBikes) {
                    Bike bike = dataSnapshot.getValue(Bike.class);
                    addBike(bike, false);
                    Log.v(TAG, "bike updated: " + bike);
                }
            }

            @Override
            public void onChildRemoved(DataSnapshot dataSnapshot) {
                synchronized (mBikes) {
                    String uuid = dataSnapshot.getKey();
                    removeBike(uuid);
                }
            }

            @Override
            public void onChildMoved(DataSnapshot dataSnapshot, String s) {
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
            }
        };
        mBikesReference.addChildEventListener(mChildEventListener);

        mAuth = FirebaseAuth.getInstance();
        mAuthListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser user = firebaseAuth.getCurrentUser();
                String userId;
                String displayName = null;

                // First, stop listing for changes
                mBikesReference.removeEventListener(mChildEventListener);

                if (user != null) {
                    // User is signed in
                    userId = user.getUid();
                    displayName = user.getDisplayName();
                    Log.d(TAG, "onAuthStateChanged:signed_in:" + userId);

                    if (!mUser.getUserId().equals(userId)) {
                        // Delete the previous user
                        mUserReference.removeValue();
                    }
                } else {
                    // User is signed out
                    Log.d(TAG, "onAuthStateChanged:signed_out");
                    userId = getOfflineUserId();
                }

                mUser.setName(displayName);
                mUser.setUserId(userId);

                mUserReference = mDatabaseReference.child("users").child(userId);
                mBikesReference = mUserReference.child("bikes");

                // Add each bike that we had before logging in
                for (String bikeUuid : mBikes.keySet()) {
                    Bike bike = mBikes.get(bikeUuid);
                    mBikesReference.child(bikeUuid).setValue(bike);
                }
                // Listen for changes again, but on the new reference
                mBikesReference.addChildEventListener(mChildEventListener);

                mUserReference.child("uid").setValue(userId);
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
    protected void onDestroy() {
        super.onDestroy();
        mAccessTokenTracker.stopTracking();
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
        if (mCallbackManager.onActivityResult(requestCode, resultCode, data)) {
            // Handled by the Facebook SDK
        } else if (requestCode == RC_SIGN_IN) {
            // Result returned from launching the Intent from GoogleSignInApi.getSignInIntent(...);
            GoogleSignInResult result = Auth.GoogleSignInApi.getSignInResultFromIntent(data);
            handleSignInResult(result);
        }
    }

    /**
     * Manipulates the map once available. This callback is triggered when the map is ready to be
     * used. This is where we can add markers or lines, add listeners or move the camera. In this
     * case, we just add a marker near Sydney, Australia. If Google Play services is not installed on
     * the device, the user will be prompted to install it inside the SupportMapFragment. This method
     * will only be triggered once the user has installed Google Play services and returned to the
     * app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        onLocationPermitted(true);
        mMap.moveCamera(CameraUpdateFactory.newLatLng(DEFAULT_LOCATION));

        mMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
            @Override
            public void onMapClick(LatLng latLng) {
                Bike bike = new Bike();
                bike.generateUuid();
                bike.setLocationLatitude(latLng.latitude);
                bike.setLocationLongitude(latLng.longitude);
                addBike(bike, true);
            }
        });
        // FIXME it shouldn't be so easy to delete bikes
        mMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(Marker marker) {
                removeBike(marker);
                return false;
            }
        });
        mMap.setOnMarkerDragListener(new GoogleMap.OnMarkerDragListener() {
            @Override
            public void onMarkerDragStart(Marker marker) {
            }

            @Override
            public void onMarkerDrag(Marker marker) {
            }

            @Override
            public void onMarkerDragEnd(Marker marker) {
                updateBike(marker);
            }
        });
    }

    private void onLocationPermitted(boolean request) {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                &&
                ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {
            if (request) {
                ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, RC_LOCATION);
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

    private String getOfflineUserId() {
        if (BuildConfig.DEBUG) {
            return "logged-out-test";
        }
        String userId = Prefs.getString(USER_ID, null);
        if (userId == null) {
            // We need to generate a new one and save it
            userId = UUID.randomUUID().toString();
            Prefs.putString(USER_ID, userId);
        }
        return userId;
    }

    private void handleSignInResult(GoogleSignInResult result) {
        Log.d(TAG, "handleSignInResult:" + result.isSuccess());
        if (result.isSuccess()) {
            // Signed in successfully, show authenticated UI.
            GoogleSignInAccount account = result.getSignInAccount();
            handleGoogleSignIn(account);
            //mStatusTextView.setText(getString(R.string.signed_in_fmt, acct.getDisplayName()));
            //updateUI(true);
        } else {
            // Signed out, show unauthenticated UI.
            //updateUI(false);
        }
    }

    private void handleGoogleSignIn(GoogleSignInAccount account) {
        Log.d(TAG, "handleGoogleSignIn:" + account.getId());
        Toast.makeText(this, "Welcome, " + account.getDisplayName(), Toast.LENGTH_LONG).show();

        AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);
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
                     // ...
                 }
             });
    }

    @Nullable
    private Bike getBikeForMarker(Marker marker) {
        String uuid = null;
        for (String markerUuid : mMarkers.keySet()) {
            if (mMarkers.get(markerUuid).equals(marker)) {
                uuid = markerUuid;
                break;
            }
        }
        if (uuid == null) {
            return null;
        }
        return mBikes.get(uuid);
    }

    private void addBike(Bike bike, boolean fromUi) {
        String uuid = bike.getUuid();
        mBikes.put(uuid, bike);
        Log.v(TAG, "bike: " + bike.toString());
        Log.v(TAG, "bikes: " + mBikes.size());

        Double lat = bike.getLocationLatitude();
        Double lon = bike.getLocationLongitude();
        if (lat == null || lon == null) {
            // We can't show a bike without a location here
            return;
        }
        LatLng latLng = new LatLng(lat, lon);
        Marker marker = mMarkers.get(uuid);
        if (marker == null) {
            // Create the marker
            marker = mMap.addMarker(new MarkerOptions().position(latLng).title(bike.getTitle()));
            marker.setDraggable(true);
            mMarkers.put(uuid, marker);
        } else {
            // Update the marker
            marker.setTitle(bike.getTitle());
            marker.setPosition(latLng);
        }

        if (fromUi) {
            // Center the map on the new marker
            CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLng(latLng);
            mMap.animateCamera(cameraUpdate);

            // Add it to Firebase
            mBikesReference.child(uuid).setValue(bike);
        }
    }

    private void updateBike(Marker marker) {
        // Update Firebase
        Bike bike = getBikeForMarker(marker);
        if (bike != null) {
            // Update the bike's position first
            bike.setLocationLatitude(marker.getPosition().latitude);
            bike.setLocationLongitude(marker.getPosition().longitude);
            updateBike(bike);
        }
    }

    private void updateBike(Bike bike) {
        mBikesReference.child(bike.getUuid()).setValue(bike);
    }

    private void removeBike(Marker marker) {
        marker.remove();
        Bike bike = getBikeForMarker(marker);
        removeBike(bike);
    }

    private void removeBike(Bike bike) {
        if (bike != null) {
            String uuid = bike.getUuid();
            removeBike(uuid);
        }
    }

    private void removeBike(String uuid) {
        if (uuid != null) {
            // Remove the bike and its marker
            mBikes.remove(uuid);
            Marker marker = mMarkers.remove(uuid);
            if (marker != null) {
                // Remove the marker from the map
                marker.remove();
            }

            // Inform Firebase of removal
            mBikesReference.child(uuid).removeValue();
        }
    }

}
