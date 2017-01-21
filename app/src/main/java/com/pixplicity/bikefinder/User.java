package com.pixplicity.bikefinder;


public class User {

  private String mUserId;
  private String name;

  public User() {
  }

  public String getUserId() {
    return mUserId;
  }

  public void setUserId(String userId) {
    mUserId = userId;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }
}
