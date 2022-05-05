package web.exception;

public class PackageNotFoundException extends RuntimeException{
    public PackageNotFoundException(String msg){
        super(msg);
    }
    public PackageNotFoundException(){

    }
}
