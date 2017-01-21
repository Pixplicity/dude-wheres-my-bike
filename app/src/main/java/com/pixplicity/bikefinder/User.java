package com.pixplicity.bikefinder;


import java.util.UUID;

public class User {

  private String mUserId;
  private String name;

  public User() {
  }

  public String getUserId() {
    return mUserId;
  }

  public void setUserId() {
    mUserId = UUID.randomUUID().toString();
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
