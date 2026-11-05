package dev.projecttimes;
public class RequestFailure extends RuntimeException {
    public final int status;
    public RequestFailure(int status,String code) { super(code); this.status=status; }
}
