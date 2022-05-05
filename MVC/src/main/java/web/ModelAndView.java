package web;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ModelAndView {
    //存储间接响应时的目标
    private String viewName;
    //存储要携带的数据（两种情况）
    private Map<String,Object> attribute = new HashMap<>();

    public Object getAttribute(String key) {
        return attribute.get(key);
    }
    public Set<String> getAttributeNames() {
        return attribute.keySet();
    }
    public void setAttribute(String key, Object value) {
        this.attribute.put(key,value);
    }
    public String getViewName() {
        return viewName;
    }
    public void setViewName(String viewName) {
        this.viewName = viewName;
    }
}
