package com.lalal.modules.result;


import com.lalal.modules.context.RequestContext;
import com.lalal.modules.enumType.ReturnCode;
import lombok.Data;

import java.io.Serializable;
import java.util.UUID;
@Data
public class Result<T> implements Serializable {
    private String message;
    private T data;
    //保证分布式事务 以及幂等处理
    private String requestId;
    private Integer code;
    Result(Integer code,String message){
        requestId= RequestContext.getRequestId();
        this.code=code;
        this.message=message;
    }
    Result(Integer code,T data){
        requestId=RequestContext.getRequestId();
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
