package com.lalal.modules.enumType;

public enum RequestStatus {

    TIMEOUT("timeout"),
    PROCESSING("processing"),
    FAILED("failed"),
    SUCCESS("success");
    RequestStatus(String name){
        this.name=name;
    }
    private final String name;
    @Override
    public String toString(){
        return name;
    }

}
