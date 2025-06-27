package org.thisway.triplog.converter;

import java.util.List;

public record KakaoCoordToAddress(
        List<Document> documents
) {
    public record Document(
            Address address
    ) {}

    public record Address(
            String address_name,
            String region_1depth_name,
            String region_2depth_name,
            String region_3depth_name
    ) {}
}
