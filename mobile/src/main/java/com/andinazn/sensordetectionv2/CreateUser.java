package com.andinazn.sensordetectionv2;

/**
 * Created by Amira Maulina on 4/7/2018.
 */

public class CreateUser {
    public CreateUser()
    {}

    public String name;
    public int age;

    public CreateUser(String name, int age, String email, String password, String code, String isSharing, String lat, String lng, String userid, String fallstate, String hrstate)
    {
        this.name = name;
        this.age = age;
        this.email = email;
        this.password = password;
        this.code = code;
        this.isSharing = isSharing;
        this.lat = lat;
        this.lng = lng;
        this.userid = userid;
        this.fallstate = fallstate;
        this.hrstate = hrstate;
    }

    public String email;
    public String password;
    public String code;
    public String isSharing;
    public String lat;
    public String lng;
    public String userid;
    public String fallstate;
    public String hrstate;


}