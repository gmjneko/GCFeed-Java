package io.github.gmjneko.gcfeed.modules.mq;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.gmjneko.gcfeed.modules.mq.entity.EventOutbox;
import io.github.gmjneko.gcfeed.modules.mq.mapper.EventOutboxMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class OutboxRepository {

    private final EventOutboxMapper mapper;

    public OutboxRepository(EventOutboxMapper mapper) {
        this.mapper = mapper;
    }

    public void insertPending(String eventId, String eventType, String payload) {
        EventOutbox row = new EventOutbox();
        row.setEventId(eventId);
        row.setEventType(eventType);
        row.setPayload(payload);
        row.setStatus(EventOutbox.PENDING);
        row.setCreatedAt(LocalDateTime.now());
        mapper.insert(row);
    }

    public List<EventOutbox> pending(int limit) {
        return mapper.selectList(Wrappers.<EventOutbox>lambdaQuery()
                .eq(EventOutbox::getStatus, EventOutbox.PENDING)
                .orderByAsc(EventOutbox::getId)
                .last("LIMIT " + limit));
    }

    public void markSent(Long id) {
        mapper.update(Wrappers.<EventOutbox>lambdaUpdate()
                .eq(EventOutbox::getId, id)
                .eq(EventOutbox::getStatus, EventOutbox.PENDING)
                .set(EventOutbox::getStatus, EventOutbox.SENT));
    }
}
