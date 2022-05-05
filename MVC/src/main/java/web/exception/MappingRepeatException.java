package web.exception;

public class MappingRepeatException extends RuntimeException{
    public MappingRepeatException(){

    }
    public MappingRepeatException(String msg){
        super(msg);
    }
}
