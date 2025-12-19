package com.lalal.modules.context;

public class RequestContext {

    private static final ThreadLocal<String> requestId=new ThreadLocal<>();

    public static void setRequestId(String requestId){
        RequestContext.requestId.set(requestId);
    };
    public static String getRequestId(){
        return RequestContext.requestId.get();
    }
    public static void clear(){
        requestId.remove();
    }


}
