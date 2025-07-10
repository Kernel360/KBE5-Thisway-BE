package org.thisway.triplog.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.thisway.component.streaming.SseConnection;
import org.thisway.component.streaming.SseEventSender;
import org.thisway.emulator.application.EmulatorService;
import org.thisway.log.domain.GpsLogData;
import org.thisway.log.dto.request.gpsLog.GpsLogEntry;
import org.thisway.log.service.LogService;
import org.thisway.triplog.dto.CoordinatesInfo;
import org.thisway.triplog.dto.response.CurrentTripLogResponse;
import org.thisway.vehicle.dto.VehicleReference;
import org.thisway.vehicle.dto.response.VehicleTrackResponse;
import org.thisway.vehicle.service.VehicleService;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class StreamCoordinatesService {

    private final EmulatorService emulatorService;
    private final LogService logService;
    private final VehicleService vehicleService;
    private final TripLogService tripLogService;

    private final SseConnection sseConnection;
    private final SseEventSender sseEventSender;

    private final Integer CHUNK_SIZE = 60;
    private final Long SSE_CHUNK_TIMEOUT = 10 * 60 * 1000L;   // 10분으로 설정

    public SseEmitter createStreamForTripLog(Long tripId) {
        SseEmitter emitter = new SseEmitter(SSE_CHUNK_TIMEOUT);
        List<CoordinatesInfo> allGpsLogsInTrip = tripLogService.getGpsLogsInTripLog(tripId);
        sendChunkedTripLog(emitter, allGpsLogsInTrip);

        return emitter;
    }

    @Async
    public void sendChunkedTripLog(SseEmitter emitter, List<CoordinatesInfo> allTripLogs) {
        List<List<CoordinatesInfo>> chunkedData = chunk(allTripLogs, CHUNK_SIZE);
        for (List<CoordinatesInfo> chunk : chunkedData) {
            sseEventSender.send(emitter, "trip_record_chunk_stream", chunk);
        }

        sseEventSender.send(emitter, "done", "complete");
        emitter.complete();
    }

    public SseEmitter createStreamForVehicle(Long vehicleId, String userName) {
        String key = generateSseKey("vehicle", vehicleId.toString(), userName);
        SseEmitter emitter = sseConnection.createSseEmitter(key);

        if (vehicleService.getVehiclePowerState(vehicleId)) {
            LocalDateTime time = tripLogService.getLastStartTimeByVehicle(vehicleId);
            List<GpsLogData> gpsLogs = logService.findGpsLogs(vehicleId, time, LocalDateTime.now(ZoneId.of("Asia/Seoul")));

            sendPastGpsLogForVehicle(key, gpsLogs, emitter);
            return emitter;
        }

        sseConnection.markInitialChunkComplete(key);
        return emitter;
    }

    @Async
    public void sendPastGpsLogForVehicle(String key, List<GpsLogData> gpsLogs, SseEmitter emitter) {
        List<List<GpsLogData>> chunkedData = chunk(gpsLogs, CHUNK_SIZE);

        for (List<GpsLogData> chunk : chunkedData) {
            sseEventSender.send(emitter, "past_gps_chunk_stream", CurrentTripLogResponse.from(chunk));
        }
        sseConnection.markInitialChunkComplete(key);
    }

    public SseEmitter createStreamForCompany(Long companyId, String userName) {
        String key = generateSseKey("company", companyId.toString(), userName);
        SseEmitter emitter = sseConnection.createSseEmitter(key);

        List<VehicleTrackResponse> vehicleTracks = vehicleService.getVehicleTracks(companyId);

        sendCurrentGpsLogForCompany(key, vehicleTracks, emitter);

        return emitter;
    }

    @Async
    public void sendCurrentGpsLogForCompany(String key, List<VehicleTrackResponse> vehicleTracks, SseEmitter emitter) {
        List<List<VehicleTrackResponse>> chunkedData = chunk(vehicleTracks, CHUNK_SIZE);

        for (List<VehicleTrackResponse> chunk : chunkedData) {
            sseEventSender.send(emitter, "current_dashboard_chunk_stream", chunk);
        }

        sseConnection.markInitialChunkComplete(key);
    }

    public void sendCurrentCoordinates(String mdn, List<GpsLogEntry> gpsLogs) {
        VehicleReference vehicle = emulatorService.getVehicleReferenceByMdn(mdn);

        sseEventSender.sendToPrefix(
                getSseKeyToSend("vehicle", vehicle.id().toString()),
                "vehicle_detail_gps_stream",
                CurrentTripLogResponse.from(gpsLogs, vehicle.id())
        );

        sseEventSender.sendToPrefix(
                getSseKeyToSend("company", vehicle.companyId().toString()),
                "dashboard_gps_stream",
                gpsLogs.stream()
                        .max(
                                Comparator.comparing((GpsLogEntry e) -> Integer.parseInt(e.min()))
                                        .thenComparing(e -> Integer.parseInt(e.sec()))
                        )
                        .map(entry -> CoordinatesInfo.from(entry, vehicle.id()))
                        .orElse(null)
        );
    }

    private String generateSseKey(String category, String id, String uniqueId) {
        return category + ":" + id + ":" + uniqueId;
    }

    private String getSseKeyToSend(String category, String id) {
        return category + ":" + id;
    }

    private <T> List<List<T>> chunk(List<T> source, int size) {
        List<List<T>> chunks = new ArrayList<>();
        for (int i = 0; i < source.size(); i += size) {
            chunks.add(source.subList(i, Math.min(i + size, source.size())));
        }

        return chunks;
    }
}
