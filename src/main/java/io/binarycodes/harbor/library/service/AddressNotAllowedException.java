package io.binarycodes.harbor.library.service;

/**
 * The unchecked face of {@link BlockedAddressException}, for the one caller that
 * needs to tell a refused address apart from an unreachable one.
 * {@code MetadataResolver.resolve} declares no checked exceptions and the dialog
 * calls it through a {@code CompletableFuture}, so the refusal travels as a runtime
 * failure rather than being folded into the offline fallback.
 */
public class AddressNotAllowedException extends RuntimeException {

    private final String address;

    public AddressNotAllowedException(BlockedAddressException blocked) {
        super(blocked.getMessage(), blocked);
        this.address = blocked.getAddress();
    }

    public String getAddress() {
        return address;
    }
}
