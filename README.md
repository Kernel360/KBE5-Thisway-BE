# Thisway-BE (렌트카 및 쉐어링카 관제를 위한 차량 관제 서비스 개발 백엔드)

<p align="center"> 
  <img width="584" height="389" alt="image" src="https://github.com/user-attachments/assets/f53a27c8-1f97-4484-a2a8-7adda024b40a" />
</p>
<img width="2559" height="1440" alt="image" src="https://github.com/user-attachments/assets/f5c0619b-e7a2-470b-96c0-8676a9441ed9" />



## 1. 프로젝트 개요

**Thisway**는 차량 운행 데이터를 기반으로 한 서비스의 백엔드 프로젝트입니다. 실시간으로 차량의 운행 기록(Trip Log)을 수집, 처리, 분석하여 사용자 및 관리자에게 유의미한 정보를 제공하는 것을 목표로 합니다.

본 프로젝트는 최신 기술 스택을 활용하여 대용량 트래픽 처리, 데이터의 정합성, 그리고 안정적인 서비스 운영을 지향합니다. 

## 2. 주요 기능

- **회원 및 인증/인가 (Member & Security)**
  - JWT 기반의 토큰 인증 시스템
  - Spring Security를 활용한 역할(Role) 기반 접근 제어 (사용자, 기업, 관리자)

<img width="1077" height="1049" alt="image" src="https://github.com/user-attachments/assets/3cf6cd2f-0e9f-4c2e-a4f9-5a479f4cb068" />
<img width="2560" height="1440" alt="image" src="https://github.com/user-attachments/assets/0dea5212-50d7-4f71-bd73-920fbd456557" />



- **차량 관리 (Vehicle)**
  - 사용자의 차량 등록, 조회, 수정, 삭제 (CRUD)
    
<img width="2559" height="1440" alt="image" src="https://github.com/user-attachments/assets/2802e16d-97f9-4802-b487-c6e28c53ff3b" />

  - 차량으로부터 운행 데이터(시동 켜짐/꺼짐, 위치 등)를 RabbitMQ를 통해 비동기적으로 수신
    
<img width="2558" height="1440" alt="image" src="https://github.com/user-attachments/assets/973fdb1a-7e92-4e16-9b55-e7689e7eed45" />


- **운행 기록 관리 (Trip Log)**
  - 수신된 데이터를 가공하여 운행 기록(Trip Log)으로 저장
    
<img width="2560" height="1440" alt="image" src="https://github.com/user-attachments/assets/9d9aea95-fd1b-45aa-b20b-afd193a2c0c7" />

  - 운행 기록 조회 및 상세 정보 제공
    
<img width="2559" height="1440" alt="image" src="https://github.com/user-attachments/assets/4ac6a91a-c479-4c85-ae4f-99bce15dd076" />


- **통계 (Statistics)**
  - Spring Batch를 활용하여 일별/월별 운행 데이터 통계 처리
  - 사용자별, 차량별 운행 거리, 시간 등 다양한 통계 데이터 제공
    
<img width="2560" height="1440" alt="image" src="https://github.com/user-attachments/assets/09b25259-5f52-43f8-8a98-b59baf3acb89" />


- **실시간 모니터링 (Monitoring)**
  - Prometheus, Grafana를 이용한 실시간 애플리케이션 및 인프라 모니터링

## 3. 기술 스택

<!-- 스타일 통일: for-the-badge / flat-square 등 한 가지만 쓰세요 -->
<!-- 색상은 simpleicons.org 기준 hex를 자주 사용합니다 -->

### 🧰 Backend
<p align="left">
  <img src="https://img.shields.io/badge/Java-21-007396?style=for-the-badge&logo=openjdk&logoColor=white" />
  <img src="https://img.shields.io/badge/Spring%20Boot-3.x-6DB33F?style=for-the-badge&logo=springboot&logoColor=white" />
  <img src="https://img.shields.io/badge/Spring%20Data%20JPA-59666C?style=for-the-badge&logo=spring&logoColor=white" />
  <img src="https://img.shields.io/badge/QueryDSL-000000?style=for-the-badge&logo=apachemaven&logoColor=white" />
  <img src="https://img.shields.io/badge/Spring%20Security-6DB33F?style=for-the-badge&logo=springsecurity&logoColor=white" />
  <img src="https://img.shields.io/badge/JWT-000000?style=for-the-badge&logo=jsonwebtokens&logoColor=white" />
  <img src="https://img.shields.io/badge/Spring%20Batch-6DB33F?style=for-the-badge&logo=spring&logoColor=white" />
  <img src="https://img.shields.io/badge/Gradle-02303A?style=for-the-badge&logo=gradle&logoColor=white" />
</p>

### 🗄️ Database & Messaging
<p align="left">
  <img src="https://img.shields.io/badge/MySQL-4479A1?style=for-the-badge&logo=mysql&logoColor=white" />
  <img src="https://img.shields.io/badge/Redis-DC382D?style=for-the-badge&logo=redis&logoColor=white" />
  <img src="https://img.shields.io/badge/RabbitMQ-FF6600?style=for-the-badge&logo=rabbitmq&logoColor=white" />
</p>

### 🚀 DevOps & Monitoring
<p align="left">
  <img src="https://img.shields.io/badge/Docker-2496ED?style=for-the-badge&logo=docker&logoColor=white" />
  <img src="https://img.shields.io/badge/Docker%20Compose-2496ED?style=for-the-badge&logo=docker&logoColor=white" />
  <img src="https://img.shields.io/badge/GitHub%20Actions-2088FF?style=for-the-badge&logo=githubactions&logoColor=white" />
  <img src="https://img.shields.io/badge/Prometheus-E6522C?style=for-the-badge&logo=prometheus&logoColor=white" />
  <img src="https://img.shields.io/badge/Grafana-F46800?style=for-the-badge&logo=grafana&logoColor=white" />
</p>

---

<details>
<summary>📋 상세 표로도 보고 싶다면 (접기/펼치기)</summary>

### Backend
| Category            | Technology                              | Version | Description              |
|---------------------|------------------------------------------|---------|--------------------------|
| **Framework**       | Spring Boot                              | 3.x     | 메인 프레임워크          |
| **Language**        | Java                                     | 21      | 주 개발 언어             |
| **Database Access** | Spring Data JPA, QueryDSL                |         | 데이터 영속성/동적 쿼리   |
| **Security**        | Spring Security, JWT                     |         | 인증 및 인가             |
| **Batch**           | Spring Batch                             |         | 대용량 배치 처리         |
| **Build Tool**      | Gradle                                   |         | 의존성 관리 및 빌드       |

### Database & Messaging
| Category          | Technology | Description                          |
|-------------------|------------|--------------------------------------|
| **RDBMS**         | MySQL      | 핵심 데이터 저장                     |
| **In-Memory DB**  | Redis      | 캐싱                                 |
| **Message Queue** | RabbitMQ   | 비동기 메시지 처리 (운행 기록 수신)  |

### DevOps & Monitoring
| Category             | Technology             | Description               |
|----------------------|------------------------|---------------------------|
| **Containerization** | Docker, Docker Compose | 개발/배포 환경 컨테이너화 |
| **CI/CD**            | GitHub Actions         | 빌드·테스트·배포 자동화   |
| **Monitoring**       | Prometheus, Grafana    | 메트릭 수집 및 시각화     |

</details>

## 4. 아키텍처

**인프라**

<img width="1118" height="703" alt="image" src="https://github.com/user-attachments/assets/31514f46-4b51-4028-b11c-13df8da3e621" />



**ERD**

<img width="1195" height="807" alt="image" src="https://github.com/user-attachments/assets/1fd08442-69ee-4dbe-8ba3-f6d73ca0f569" />



## 5. 디렉토리 구조

```
.
├── .github/            # GitHub Actions (CI/CD) 워크플로우
├── infra/              # Docker Compose, 설정 등 인프라 관련 파일
│   ├── dev/            # 개발 환경 인프라 (docker-compose.yml)
│   └── prod/           # 운영 환경 인프라
├── src/
│   ├── main/
│   │   ├── java/org/thisway/
│   │   │   ├── ThiswayApplication.java
│   │   │   ├── common/         # 예외 처리, 공통 응답 등
│   │   │   ├── config/         # 각종 설정 (Security, RabbitMQ, QueryDSL)
│   │   │   ├── component/      # 외부 시스템 연동 (Redis, Email)
│   │   │   ├── member/         # 회원 도메인
│   │   │   ├── vehicle/        # 차량 도메인
│   │   │   ├── triplog/        # 운행 기록 도메인
│   │   │   ├── statistics/     # 통계 도메인 (Batch 포함)
│   │   │   └── security/       # 인증/인가 관련 로직
│   │   └── resources/      # application.yml, 정적 파일 등
│   └── test/             # 테스트 코드
├── build.gradle        # 프로젝트 빌드 및 의존성 관리
└── README.md           # 프로젝트 소개
```
