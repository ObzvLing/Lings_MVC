package web;

import java.io.InputStream;

public class MVCfile {
    private String key;
    private String fileName;
    private String contentType;
    private long size;
    private byte[] content;
    private InputStream inputStream;

    public MVCfile(){}
    public MVCfile(String key, String fileName, String contentType, long size, byte[] content, InputStream inputStream){
        this.key = key;
        this.fileName = fileName;
        this.contentType = contentType;
        this.size = size;
        this.content = content;
        this.inputStream = inputStream;
    }
}
