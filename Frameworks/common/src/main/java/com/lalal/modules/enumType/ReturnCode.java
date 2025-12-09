package com.lalal.modules.enumType;

public enum ReturnCode {
    success(200),

    SEVER_ERROR(505);

    private Integer code;
    ReturnCode(Integer code){
        this.code=code;
    }
    public Integer code(){
        return code;
    }
}
