package org.spongepowered.common.command.exception;

import org.spongepowered.api.text.Text;

public class CommandRuntimeException extends RuntimeException {

    private final Text errorMessage;

    public CommandRuntimeException(Text errorMessage) {
        super(errorMessage.toPlain());
        this.errorMessage = errorMessage;
    }

    public CommandRuntimeException(Text errorMessage, Throwable inner) {
        super(errorMessage.toPlain(), inner);
        this.errorMessage = errorMessage;
    }

    public Text getErrorMessage() {
        return this.errorMessage;
    }

}
