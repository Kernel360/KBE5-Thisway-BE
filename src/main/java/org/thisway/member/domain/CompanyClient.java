package org.thisway.member.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CompanyClient {

    Optional<CompanyInfo> findById(long id);

    CompanyInfo getById(long id);

    List<CompanyInfo> findAllById(Collection<Long> ids);

    List<CompanyInfo> findAllByMember(Collection<Member> members);

    boolean existById(Long companyId);
}
