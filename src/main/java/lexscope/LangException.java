package lexscope;

public final class LangException extends RuntimeException {
    private final String code;
    public LangException(String code, String message) { super(message); this.code = code; }
    public String code() { return code; }
}
