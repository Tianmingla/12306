package com.lalal.modules.enumType;

public enum DelTagEnum {
    DELETED(1),
    NORMAL(0);
    private int code;
    private DelTagEnum(int code){
        this.code=code;
    }
    public int code(){
        return this.code;
    }
}
