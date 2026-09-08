package org.thisway.emulator.credential;

/** Server-derived admission identity. Consumers must revalidate the binding before writing. */
public record DeviceIdentity(long emulatorId, long vehicleId, long companyId, String mdn,
                             long assignmentRevision) {
    @Override
    public String toString() {
        return "DeviceIdentity[emulatorId=" + emulatorId + ", vehicleId=" + vehicleId
                + ", companyId=" + companyId + ", assignmentRevision=" + assignmentRevision + "]";
    }
}
