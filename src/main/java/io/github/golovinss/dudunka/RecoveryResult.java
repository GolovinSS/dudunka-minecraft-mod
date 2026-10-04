package io.github.golovinss.dudunka;

/** Server-authored outcomes; failed operations retain their existing safety checks. */
public record RecoveryResult(Code code,int retrySeconds) {
    public enum Code { APPLIED, RETURNED, CARRIER_RESTORED, REJECTED, OWNER_UNSAFE, NO_SPACE, TETHERED, NOT_FOUND, COOLDOWN, BUSY, CARRIER_EXISTS, NO_SLOT, NO_BACKUP, DIMENSION_UNAVAILABLE, TRANSFER_FAILED, FURNITURE_ASSIGNED, FURNITURE_CLEARED, NO_FURNITURE, REQUEST_ACCEPTED, REQUEST_COMPLETED, REQUEST_SKIPPED, REQUEST_NOT_READY }
    public RecoveryResult { if(retrySeconds<0 || retrySeconds>10)throw new IllegalArgumentException("Invalid retry time"); }
    public static RecoveryResult of(Code code){return new RecoveryResult(code,0);}
    public boolean accepted(){return code==Code.APPLIED || code==Code.RETURNED || code==Code.CARRIER_RESTORED || code==Code.FURNITURE_ASSIGNED || code==Code.FURNITURE_CLEARED || code==Code.REQUEST_ACCEPTED || code==Code.REQUEST_COMPLETED || code==Code.REQUEST_SKIPPED;}
}
