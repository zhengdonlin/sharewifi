package com.verifone.sharewifi;

public class WifiInfo {

    public WifiInfo(String ssid, String password) {
        this.ssid = ssid;
        this.password = password;
    }

    private String ssid;

    private String password;



    public String getSsid() {
        return ssid;
    }

    public void setSsid(String ssid) {
        this.ssid = ssid;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public String toString(){
        return getPassword()+getSsid();

    }



}
