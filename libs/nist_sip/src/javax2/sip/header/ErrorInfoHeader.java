package javax2.sip.header;

import java.text.ParseException;

import javax2.sip.address.URI;

public interface ErrorInfoHeader extends Header, Parameters {
    String NAME = "Error-Info";

    URI getErrorInfo();
    void setErrorInfo(URI errorInfo);

    String getErrorMessage();
    void setErrorMessage(String errorMessage) throws ParseException;
}
