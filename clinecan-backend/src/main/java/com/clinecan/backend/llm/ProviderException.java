package com.clinecan.backend.llm;
import com.clinecan.backend.model.AgentError;

/** Only curated messages cross the provider boundary; never attach raw causes or response bodies. */
public class ProviderException extends RuntimeException {
    private final AgentError error;
    public ProviderException(String code, String message, boolean retryable) {
        super(message);
        this.error = new AgentError(code, message, retryable);
    }
    public AgentError error() { return error; }
    public static ProviderException malformed() {
        return new ProviderException("INVALID_OUTPUT", "Sağlayıcı geçerli proje verisi döndürmedi.", false);
    }
}
