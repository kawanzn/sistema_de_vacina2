package br.com.easyvacc.android;

/** Native-flow state. A selected target never means that a message was delivered. */
public final class ShareFlow {
    public enum State { READY, PREVIEW, CHOOSER, SELECTED, CANCELLED, ERROR }
    private State state = State.READY;
    private String text = "";
    public State state() { return state; }
    public String text() { return text; }
    public void preview(String value) {
        if (state == State.CHOOSER) throw new IllegalStateException("Aguarde o compartilhamento atual.");
        if (value == null || value.trim().isEmpty() || value.length() > 600)
            throw new IllegalArgumentException("Confira o endereço antes de compartilhar.");
        text = value.trim(); state = State.PREVIEW;
    }
    public void launch() {
        if (state != State.PREVIEW && state != State.CANCELLED && state != State.ERROR && state != State.SELECTED)
            throw new IllegalStateException("Confira o endereço antes de compartilhar.");
        state = State.CHOOSER;
    }
    public void cancel() { state = State.CANCELLED; }
    public void fail() { state = State.ERROR; }
    public void selected() {
        if (state != State.CHOOSER) throw new IllegalStateException("Nenhum compartilhamento em andamento.");
        state = State.SELECTED;
    }
    public void restore(String value, String savedState) {
        text = value == null ? "" : value;
        try { state = State.valueOf(savedState); } catch (Exception e) { state = State.READY; }
    }
}
