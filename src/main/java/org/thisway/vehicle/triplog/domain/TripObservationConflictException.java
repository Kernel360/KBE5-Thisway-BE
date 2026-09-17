package org.thisway.vehicle.triplog.domain;

public class TripObservationConflictException extends RuntimeException {
    public TripObservationConflictException() {
        super("Trip observation conflicts with an existing observation");
    }
}
