package org.thisway.company.entity;

import jakarta.persistence.Entity;
import lombok.Getter;
import org.thisway.common.BaseEntity;

@Entity
@Getter
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
}
