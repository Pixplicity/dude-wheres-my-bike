package com.pixplicity.bikefinder;

import java.util.UUID;

public class Bike {

    private String mUuid;
    private String mTitle;
    private Double mLocationLatitude;
    private Double mLocationLongitude;

    public Bike() {
    }

    public void generateUuid() {
        mUuid = UUID.randomUUID().toString();
    }

    public void setUuid(String uuid) {
        mUuid = uuid;
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

    public Double getLocationLongitude() {
        return mLocationLongitude;
    }

    public void setLocationLongitude(Double locationLongitude) {
        mLocationLongitude = locationLongitude;
    }

    public Double getLocationLatitude() {
        return mLocationLatitude;
    }

    public void setLocationLatitude(Double locationLatitude) {
        mLocationLatitude = locationLatitude;
    }

    @Override
    public String toString() {
        return Bike.class.getSimpleName() + "{" +
                "mUuid='" + mUuid + '\'' +
                ", mTitle='" + mTitle + '\'' +
                ", mLocationLatitude=" + mLocationLatitude +
                ", mLocationLongitude=" + mLocationLongitude +
                '}';
    }
}
