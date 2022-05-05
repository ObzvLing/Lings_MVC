package web;

import java.lang.reflect.Method;

/**
 * 是一个实体类domain
 * 用来存储请求映射缓存信息
 */
public class MappingInfo {
    private String reqPath;
    private Object controller;
    private Method method;

    public MappingInfo(String reqPath, Object controller, Method method){
        this.controller = controller;
        this.reqPath = reqPath;
        this.method = method;
    }

    public String getReqPath() {
        return reqPath;
    }

    public Object getController() {
        return controller;
    }

    public Method getMethod() {
        return method;
    }

    public void setReqPath(String reqPath) {
        this.reqPath = reqPath;
    }

    public void setController(Object controller) {
        this.controller = controller;
    }

    public void setMethod(Method method) {
        this.method = method;
    }
}
