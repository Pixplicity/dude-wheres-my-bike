package com.pixplicity.bikefinder.utils;

import com.google.firebase.database.FirebaseDatabase;

public class DatabaseUtils {

    private static FirebaseDatabase sFirebaseDatabase;

    public static FirebaseDatabase getFirebaseDatabase() {
        if (sFirebaseDatabase == null) {
            sFirebaseDatabase = FirebaseDatabase.getInstance();
            // By enabling persistence, any data that we sync while online will be
            // persisted to disk and available offline, even when we restart the app.
            sFirebaseDatabase.setPersistenceEnabled(true);
        }
        return sFirebaseDatabase;
    }

}
