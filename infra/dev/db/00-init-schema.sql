create table company
(
    id          bigint auto_increment
        primary key,
    active      bit          not null,
    created_at  datetime(6)  not null,
    updated_at  datetime(6)  null,
    addr_detail varchar(255) not null,
    addr_road   varchar(255) not null,
    contact     varchar(255) not null,
    crn         varchar(255) not null,
    gps_cycle   int          not null,
    memo        varchar(255) not null,
    name        varchar(255) not null
) ENGINE=InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

create table member
(
    id         bigint auto_increment
        primary key,
    active     bit                                                       not null,
    created_at datetime(6)                                               not null,
    updated_at datetime(6)                                               null,
    email      varchar(255)                                              not null,
    memo       varchar(255)                                              not null,
    name       varchar(255)                                              not null,
    password   varchar(255)                                              not null,
    phone      varchar(255)                                              not null,
    role       enum ('ADMIN', 'COMPANY_ADMIN', 'COMPANY_CHEF', 'MEMBER') not null,
    company_id bigint                                                    not null,
    constraint UKmbmcqelty0fbrvxp1q58dn57t
        unique (email),
    constraint FKax2gealrg44mnq3ibas3q9de6
        foreign key (company_id) references company (id)
) ENGINE=InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

create table vehicle_model
(
    id           bigint auto_increment
        primary key,
    active       bit          not null,
    created_at   datetime(6)  not null,
    updated_at   datetime(6)  null,
    manufacturer varchar(255) not null,
    model_year   int          not null,
    name         varchar(255) not null
) ENGINE=InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;


create table vehicle
(
    id               bigint auto_increment
        primary key,
    active           bit          not null,
    created_at       datetime(6)  not null,
    updated_at       datetime(6)  null,
    car_number       varchar(255) not null,
    color            varchar(255) not null,
    latitude         double       null,
    longitude        double       null,
    mileage          int          not null,
    power_on         bit          not null,
    company_id       bigint       not null,
    vehicle_model_id bigint       not null,
    constraint FK8l9m1j8m30mdmdcbbt1c4trkd
        foreign key (company_id) references company (id),
    constraint FKjtchj1qk6y3jdm3ycsbaoci6q
        foreign key (vehicle_model_id) references vehicle_model (id)
);

create table emulator
(
    id                      bigint auto_increment
        primary key,
    device_firmware_version varchar(255) not null,
    device_id               int          not null,
    manufacture_id          int          not null,
    mdn                     varchar(255) not null,
    packet_version          int          not null,
    terminal_id             varchar(255) not null,
    vehicle_id              bigint       not null,
    constraint UKdgvba1n7muqy18h8uvtak9hbp
        unique (mdn),
    constraint FK90yv0q9wnhe5fhcnursjsgk8p
        foreign key (vehicle_id) references vehicle (id)
);

create table geofence_log
(
    id                bigint auto_increment
        primary key,
    vehicle_id        bigint                              not null,
    mdn               varchar(20)                         not null,
    occured_time      datetime                            not null,
    geofence_group_id bigint                              null,
    geofence_id       bigint                              null,
    event_val         tinyint                             null,
    gps_status        varchar(10)                         null,
    latitude          double                              null,
    longitude         double                              null,
    angle             int                                 null,
    created_at        timestamp default CURRENT_TIMESTAMP null,
    constraint geofence_log_ibfk_1
        foreign key (vehicle_id) references vehicle (id)
);

create index idx_geofence_log_geofence_id
    on geofence_log (geofence_id);

create index idx_geofence_log_mdn
    on geofence_log (mdn);

create index idx_geofence_log_occured_time
    on geofence_log (occured_time);

create index idx_geofence_log_vehicle_id
    on geofence_log (vehicle_id);

create table gps_log
(
    id               bigint auto_increment
        primary key,
    vehicle_id       bigint                              not null,
    mdn              varchar(20)                         not null,
    gps_status       varchar(10)                         null,
    latitude         double                              null,
    longitude        double                              null,
    angle            int                                 null,
    speed            int                                 null,
    total_trip_meter int                                 null,
    battery_voltage  int                                 null,
    occurred_time    datetime                            not null,
    created_at       timestamp default CURRENT_TIMESTAMP null,
    constraint gps_log_ibfk_1
        foreign key (vehicle_id) references vehicle (id)
);

create index idx_gps_log_mdn
    on gps_log (mdn);

create index idx_gps_log_occurred_time
    on gps_log (occurred_time);

create index idx_gps_log_vehicle_id
    on gps_log (vehicle_id);

create table power_log
(
    id               bigint auto_increment
        primary key,
    vehicle_id       bigint                              not null,
    mdn              varchar(20)                         not null,
    power_status     tinyint                             not null,
    power_time       datetime                            not null,
    gps_status       varchar(10)                         null,
    latitude         double                              null,
    longitude        double                              null,
    total_trip_meter int                                 null,
    created_at       timestamp default CURRENT_TIMESTAMP null,
    constraint power_log_ibfk_1
        foreign key (vehicle_id) references vehicle (id)
);

create index idx_power_log_mdn
    on power_log (mdn);

create index idx_power_log_power_time
    on power_log (power_time);

create index idx_power_log_vehicle_id
    on power_log (vehicle_id);

create table trip_log
(
    id               bigint auto_increment
        primary key,
    active           bit          not null,
    created_at       datetime(6)  not null,
    updated_at       datetime(6)  null,
    end_time         datetime(6)  null,
    off_addr         varchar(255) null,
    off_addr_detail  varchar(255) null,
    off_latitude     double       null,
    off_longitude    double       null,
    on_addr          varchar(255) null,
    on_addr_detail   varchar(255) null,
    on_latitude      double       null,
    on_longitude     double       null,
    start_time       datetime(6)  not null,
    total_trip_meter int          not null,
    vehicle_id       bigint       not null,
    constraint trip_log_ibfk_1
        foreign key (vehicle_id) references vehicle (id)
);

create index idx_trip_log_vehicle_id
    on power_log (vehicle_id);

