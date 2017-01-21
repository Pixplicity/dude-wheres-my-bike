package com.pixplicity.bikefinder;

import com.google.android.gms.maps.model.LatLng;
import java.util.UUID;

public class Bike {

  private String mUuid;
  private String mTitle;
  private LatLng mLocation;

  public Bike() {
    mUuid = UUID.randomUUID().toString();
  }

  public String getUuid() {
    return mUuid;
  }

  public String getTitle() {
    return mTitle;
  }

  public void setTitle(String title) {
    mTitle = title;
  }

  public LatLng getLocation() {
    return mLocation;
  }

  public void setLocation(LatLng location) {
    mLocation = location;
  }

}
