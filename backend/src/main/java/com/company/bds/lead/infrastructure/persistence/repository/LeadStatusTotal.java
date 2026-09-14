package com.company.bds.lead.infrastructure.persistence.repository;

import com.company.bds.lead.domain.model.LeadStatus;

public interface LeadStatusTotal {
    LeadStatus getStatus();
    long getTotal();
}
