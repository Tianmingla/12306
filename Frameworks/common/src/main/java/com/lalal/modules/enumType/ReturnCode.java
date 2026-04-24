package com.lalal.modules.enumType;

public enum ReturnCode {
    success(200),
    fail(404),
    unauthorized(401),
    failedAuthorized(402),
    unUse(411),
    SEVER_ERROR(505);

    private Integer code;
    ReturnCode(Integer code){
        this.code=code;
    }
    public Integer code(){
        return code;
    }
}
