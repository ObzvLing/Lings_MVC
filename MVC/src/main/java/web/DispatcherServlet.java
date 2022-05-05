package web;

import com.alibaba.fastjson.JSON;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import web.annotation.*;
import web.exception.*;
import javax.servlet.*;
import javax.servlet.annotation.WebInitParam;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;

/**
 * web框架的主入口，负责映射与请求的分发
 */
@WebServlet(//value值为包路径，使用前设置
        loadOnStartup = 0,
        initParams = {@WebInitParam(name="packageScan", value="")},
        urlPatterns = {"/"}
)
public class DispatcherServlet extends HttpServlet {
    private Map<String,MappingInfo> map = new HashMap<>();
    //参数缓存
    private Map<String, Object> paramCache = new HashMap<>();

    public void init() throws ServletException {
        String packagePath = this.getInitParameter("packageScan");
        String dirPath = packagePath.replace(".","/");
        String basePath = Thread.currentThread().getContextClassLoader().getResource("").getPath();
        dirPath = basePath + dirPath.substring(1);
        File dir = new File(dirPath);
        if(!dir.exists()){
            throw new PackageNotFoundException(packagePath);
        }
        for(String name:dir.list()){
            if(name.endsWith(".class")){
                name = name.replace(".class","");
                String classPath = packagePath + "." + name;
                loadConfig(classPath);
            }
        }
    }
    private void loadConfig(String classPath){
        try {
            classPath = classPath.substring(1);
            Class clazz = Class.forName(classPath);
            Controller controllerAnnotation = (Controller) clazz.getAnnotation(Controller.class);
            if (controllerAnnotation == null){
                return;
            }
            Method[] methods = clazz.getDeclaredMethods();
            Object controller = null;
            for(Method method:methods){
                RequestMapping requestMapping = method.getAnnotation(RequestMapping.class);
                if (requestMapping == null){
                    continue;
                }
                String[] reqPath = requestMapping.value();
                for(String req : reqPath) {
                    if(controller == null){
                        controller = clazz.newInstance();
                    }
                    MappingInfo mapping = new MappingInfo(req, controller, method);
                    Object old = map.get(req);
                    if (old != null){
                        throw new MappingRepeatException(req + "请求被重复映射");
                    }
                    map.put(req,mapping);
                }
            }
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String uri = req.getRequestURI();
        String contextPath = req.getContextPath();
        if(!contextPath.equals("/")) {
            uri = uri.replace(contextPath, "");
        }
        MappingInfo mappingInfo = map.get(uri);
        if (mappingInfo != null){
            try{
                dynamicHandler(mappingInfo,req,resp);
            }catch (InvocationTargetException e){
                e.printStackTrace();
            }catch (IllegalAccessException e){
                e.printStackTrace();
            }
        }else{
            //利用realPath获得web部署到服务器以后的根路径
            staticHandler(uri,req,resp);
        }
    }
    /**
     * 处理静态资源
     * IO读取指定文件（相对路径——>绝对路径）
     * 如果没找到对应文件资源，抛出404
     * @param reqPath
     * @param request
     * @param response
     * @throws IOException
     */
    private void staticHandler(String reqPath, HttpServletRequest request, HttpServletResponse response) throws IOException{
        String webPath = request.getServletContext().getRealPath("");
        String filePath = webPath + reqPath.substring(1);
        File file = new File(filePath);
        if(file.exists()){
            if(file.getName().endsWith(".html")){
                response.setContentType("text/html;charset=utf-8");
            }else if(file.getName().endsWith(".css")){
                response.setContentType("text/css;charset=utf-8");
            }else if(file.getName().endsWith(".javascript")){
                response.setContentType("text/javascript;charset=utf-8");
            }else if(file.getName().endsWith(".png")){
                response.setContentType("image/png");
            }else if(file.getName().endsWith(".jpg")){
                response.setContentType("image/jpeg");
            }
            FileInputStream fileInputStream = new FileInputStream(file);
            byte[] bytes = new byte[0x100];//256
            int length;
            while ((length = fileInputStream.read(bytes)) != -1){
                response.getOutputStream().write(bytes,0,length);
            }
            fileInputStream.close();
        }else{
            response.sendError(404,reqPath);
        }
    }
    /**
     * 处理动态资源
     * @param mapping
     * @param request
     * @param response
     * @throws InvocationTargetException
     * @throws IllegalAccessException
     * @throws UnsupportedEncodingException
     */
    private void dynamicHandler(MappingInfo mapping, HttpServletRequest request, HttpServletResponse response) throws InvocationTargetException, IllegalAccessException, UnsupportedEncodingException {
        //获取参数
        loadParameters(request);
        Object controller = mapping.getController();
        Method method = mapping.getMethod();//当前请求的映射信息
        //根据映射方法的参数列表，处理请求传递的参数
        Object[] paramArray = handleParam(method,request,response);
        Object result = method.invoke(controller,paramArray);
        //根据controller方法的返回值给予响应
        try {
            handleResponse(result,method,request,response);
        } catch (ServletException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private void loadParameters(HttpServletRequest request) throws UnsupportedEncodingException {
        //获取请求传参
        request.setCharacterEncoding("utf-8");
        {
            Enumeration<String> keys = request.getParameterNames();
            while(keys.hasMoreElements()){
                String key = keys.nextElement();
                //针对存在多个同名参数
                String[] value = request.getParameterValues(key);
                paramCache.put(key,value);
            }
        }
        //获取文件传参
        {
            try {
                DiskFileItemFactory factory = new DiskFileItemFactory();
                ServletFileUpload upload = new ServletFileUpload(factory);
                List<FileItem> fis = upload.parseRequest(request);
                for(FileItem fi:fis){
                    if(fi.isFormField()){
                        String key = fi.getFieldName();
                        String value = fi.getString("utf-8");
                        Object oldValue = paramCache.get(key);
                        if(oldValue == null){
                            oldValue = new String[]{value};
                            paramCache.put(key,oldValue);
                        }else{
                            String[] oldValueArray = (String[])oldValue;
                            String[] newValueArray = Arrays.copyOf(oldValueArray,oldValueArray.length+1);
                            newValueArray[newValueArray.length-1] = value;
                            paramCache.put(key,newValueArray);
                        }
                        paramCache.put(key,value);
                    }else{
                        String key = fi.getFieldName();
                        String fileName = fi.getName();
                        long size = fi.getSize();
                        String contentType = fi.getContentType();//xml中配置的MIME-TYPE
                        byte[] content = fi.get();
                        InputStream is = fi.getInputStream();
                        MVCfile mvcFile = new MVCfile(key,fileName,contentType,size,content,is);
                        Object oldValue = paramCache.get(key);
                        if(oldValue== null){
                            oldValue = new MVCfile[]{mvcFile};
                            paramCache.put(key,oldValue);
                        }else{
                            MVCfile[] oldValueArray = (MVCfile[])oldValue;
                            MVCfile[] newValueArray = Arrays.copyOf(oldValueArray,oldValueArray.length-1);
                            newValueArray[newValueArray.length-1] = mvcFile;
                            paramCache.put(key,newValueArray);
                        }
                    }
                }
            } catch (FileUploadException e) {
                //技术上认为有错，但逻辑上没错。不是每次上传的都是文件请求，也有普通请求
                System.out.println("[warning]: 不是一个文件上传请求");
            }catch (UnsupportedEncodingException e){
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    private Object[] handleParam(Method method,HttpServletRequest request, HttpServletResponse response) throws InvocationTargetException, IllegalAccessException {
        Parameter[] parameters = method.getParameters();//获取controller的参数列表
        int paramLength = parameters.length;//有几个参数，就需要框架传递多少参数
        Object[] paramArray = new Object[paramLength];
        int i = 0;
        for(Parameter parameter:parameters){
            RequestParam requestParam = parameter.getAnnotation(RequestParam.class);
            if(requestParam!=null){
                Object value = paramCache.get(requestParam.value());
                if(value == null){
                    continue;
                }
                Class paramType = parameter.getType();
                value = castType(value,paramType);
                paramArray[i++] = value;
            }else{
                Class paramType = parameter.getType();
                if(paramType == HttpServletRequest.class || paramType == ServletRequest.class){
                    paramArray[i++] = request;
                    continue;
                }else if(paramType == HttpServletResponse.class || paramType == ServletResponse.class){
                    paramArray[i++] = response;
                    continue;
                }else if(paramType == HttpSession.class){
                    paramArray[i++] = request.getSession();
                    continue;
                }
                try {
                    Object paramObj = paramType.newInstance();
                    Method[] ms = paramType.getMethods();
                    for(Method m:ms){
                        String methodName = m.getName();
                        if(methodName.startsWith("set")){
                            String key = methodName.substring(3);
                            if(key.length() == 1){
                                key = key.toLowerCase();
                            }else{
                                key = key.substring(0,1).toLowerCase() + key.substring(1);
                            }
                            Object value = paramCache.get(key);
                            if(value == null){
                                continue;
                            }else{
                                Class setParamType = m.getParameterTypes()[0];
                                value = castType(value,setParamType);
                                m.invoke(paramObj,value);
                            }
                        }
                    }
                    paramArray[i++] = paramObj;
                } catch (InstantiationException e) {
                    e.printStackTrace();
                }
            }
        }
        return paramArray;
    }
    private void handleResponse(Object result,Method method, HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if(result == null){
            return;
        }
        ResponseBody responseBody = method.getAnnotation(ResponseBody.class);
        String resultStr = null;
        boolean isRedirect = false;
        if(result instanceof ModelAndView){
            ModelAndView mav = (ModelAndView) result;
            resultStr = mav.getViewName();
            Set<String> keys = mav.getAttributeNames();
            if(isRedirect = resultStr.startsWith("redirect:")){
                resultStr += "?";
                for(String key:keys){
                    Object value = mav.getAttribute(key);
                    resultStr += key + "=" + value + "&";
                }
            }else{
                for(String key:keys) {
                    Object value = mav.getAttribute(key);
                    request.setAttribute(key,value);
                }
            }
        }else{
            resultStr = (String) result;
        }
        if(responseBody == null){
            if(isRedirect || resultStr.startsWith("redirect:")){
                int i = resultStr.indexOf(":");
                resultStr = resultStr.substring(i+1);
                response.sendRedirect(resultStr);
            }else{
                request.getRequestDispatcher(resultStr).forward(request,response);//默认转发
            }
        }else{
            if(result instanceof String){
                resultStr = (String) result;
                response.setContentType("text/html;charset=utf-8");
            }else{
                resultStr = JSON.toJSONString(result);
                response.setContentType("text/json;charset=utf-8");
            }
            response.getWriter().write(resultStr);
        }
    }
    private Object castType(Object value, Class type){
        if(type == String.class){
            String[] array = (String[])value;
            return array[0];
        }else if(type == String[].class){
            return (String[])value;
        }else if(type == MVCfile.class){
            MVCfile[] array = (MVCfile[])value;
            return array[0];
        }else if(type == MVCfile[].class){
            return (MVCfile[])value;
        }else if(type == int.class || type == Integer.class){
            String[] array = (String[])value;
            return new Integer(array[0]);
        }else if(type == double.class || type == Double.class){
            String[] array = (String[])value;
            return new Double(array[0]);
        }else if(type == int[].class){
            String[] array = (String[])value;
            int[] intArray = new int[array.length];
            for(int i = 0; i<array.length; i++){
                intArray[i] = Integer.parseInt(array[i]);
            }
            return intArray;
        }else if(type == Integer[].class){
            String[] array = (String[])value;
            Integer[] intArray = new Integer[array.length];
            for(int i = 0; i<array.length; i++){
                intArray[i] = new Integer(array[i]);
            }
            return intArray;
        }
        return value;
    }
}
