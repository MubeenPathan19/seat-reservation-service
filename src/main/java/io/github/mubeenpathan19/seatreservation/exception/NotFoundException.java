package io.github.mubeenpathan19.seatreservation.exception;
import org.springframework.http.HttpStatus;
public class NotFoundException extends DomainException {
    public NotFoundException(String code,String message){super(HttpStatus.NOT_FOUND,code,message);}
}
