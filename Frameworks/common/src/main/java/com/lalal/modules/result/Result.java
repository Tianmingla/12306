package com.lalal.modules.result;


import com.lalal.modules.enumType.ReturnCode;
import lombok.Data;

import java.io.Serializable;
import java.util.UUID;
@Data
public class Result<T> implements Serializable {
    private String message;
    private T data;
    private String requestId;
    private Integer code;
    Result(Integer code,String message){
        requestId= UUID.randomUUID().toString();
        this.code=code;
        this.message=message;
    }
    Result(Integer code,T data){
        requestId=UUID.randomUUID().toString();
        this.data=data;
        this.code=code;
    }
    public static <T> Result<T> success(T data){
        return new Result<>(ReturnCode.success.code(),data);
    }
    public static <T> Result<T> success(String message){
        return new Result<>(ReturnCode.success.code(),message);
    }


}
