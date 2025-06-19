package org.thisway.triplog.converter;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.thisway.common.CustomException;
import org.thisway.common.ErrorCode;
import org.thisway.triplog.dto.KakaoCoordToAddress;
import org.thisway.triplog.dto.ReverseGeocodeResult;

import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class ReverseGeocodingConverter {

    @Value("${kakao.rest-api-key}")
    private String kakaoApiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    public ReverseGeocodeResult convertToAddress(double latitude, double longitude) {
        String url = String.format(
                "https://dapi.kakao.com/v2/local/geo/coord2address.json?x=%f&y=%f"
                , longitude, latitude
        );

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "KakaoAK " + kakaoApiKey);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<KakaoCoordToAddress> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                KakaoCoordToAddress.class
        );

        List<KakaoCoordToAddress.Document> addresses = Objects.requireNonNull(response.getBody()).documents();

        if (addresses == null || addresses.isEmpty()) {
            throw new CustomException(ErrorCode.TRIP_LOG_ADDRESS_NOT_FOUND);
        }

        KakaoCoordToAddress.Address address = addresses.getFirst().address();

        String fullAddress = address.address_name();
        String addr = String.format(
                "%s %s %s",
                address.region_1depth_name(),
                address.region_2depth_name(),
                address.region_3depth_name()
        );

        String addrDetail = fullAddress.replace(addr, "").trim();

        return new ReverseGeocodeResult(
                addr,
                addrDetail
        );
    }

}
