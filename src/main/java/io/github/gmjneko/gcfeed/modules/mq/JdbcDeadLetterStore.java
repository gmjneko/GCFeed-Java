package io.github.gmjneko.gcfeed.modules.mq;

import io.github.gmjneko.gcfeed.modules.mq.entity.GovernanceDeadLetter;
import io.github.gmjneko.gcfeed.modules.mq.mapper.GovernanceDeadLetterMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public class JdbcDeadLetterStore implements DeadLetterStore {

    private final GovernanceDeadLetterMapper mapper;

    public JdbcDeadLetterStore(GovernanceDeadLetterMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void record(String taskType, String payload, int retryCount, String lastError) {
        GovernanceDeadLetter row = new GovernanceDeadLetter();
        row.setTaskType(taskType);
        row.setPayload(payload == null ? "{}" : payload);
        row.setRetryCount(retryCount);
        row.setStatus(GovernanceDeadLetter.ABANDONED);
        String error = lastError == null ? "" : lastError;
        row.setLastError(error.length() > 512 ? error.substring(0, 512) : error);
        LocalDateTime now = LocalDateTime.now();
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        mapper.insert(row);
    }
}
