package org.thisway.company.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.thisway.support.common.BaseEntity;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Company extends BaseEntity {
    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String crn;

    @Column(nullable = false)
    private String contact;

    @Column(nullable = false)
    private String addrRoad;

    @Column(nullable = false)
    private String addrDetail;

    @Column(nullable = false)
    private String memo;

    @Column(nullable = false)
    private Integer gpsCycle;

    @Builder
    public Company(
            String name,
            String crn,
            String contact,
            String addrRoad,
            String addrDetail,
            String memo,
            Integer gpsCycle
    ) {
        this.name = name;
        this.crn = crn;
        this.contact = contact;
        this.addrRoad = addrRoad;
        this.addrDetail = addrDetail;
        this.memo = memo;
        this.gpsCycle = gpsCycle;
    }

    public void updateName(String name) {
        this.name = name;
    }

    public void updateCrn(String crn) {
        this.crn = crn;
    }

    public void updateContact(String contact) {
        this.contact = contact;
    }

    public void updateAddrRoad(String addrRoad) {
        this.addrRoad = addrRoad;
    }

    public void updateAddrDetail(String addrDetail) {
        this.addrDetail = addrDetail;
    }

    public void updateMemo(String memo) {
        this.memo = memo;
    }

    public void updateGpsCycle(Integer gpsCycle) {
        this.gpsCycle = gpsCycle;
    }
}
