package com.depository_manage.service.aps.impl;

import com.mongodb.client.model.changestream.ChangeStreamDocument;
import com.mongodb.client.model.changestream.OperationType;
import com.depository_manage.entity.aps.ShiftSchedule;
import com.depository_manage.mapper.aps.ShiftScheduleMapper;
import lombok.extern.slf4j.Slf4j;
import org.bson.BsonObjectId;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.messaging.ChangeStreamRequest;
import org.springframework.data.mongodb.core.messaging.DefaultMessageListenerContainer;
import org.springframework.data.mongodb.core.messaging.Message;
import org.springframework.data.mongodb.core.messaging.Subscription;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;

@Slf4j
@Component
public class MongoShiftScheduleSyncListener {

    private static final String COLLECTION_NAME = "productionCalendarDtl";

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource
    private MongoTemplate mongoTemplate;

    @Resource
    private ShiftScheduleMapper shiftScheduleMapper;

    private DefaultMessageListenerContainer listenerContainer;

    private Subscription subscription;

    @PostConstruct
    public void startListener() {

        listenerContainer = new DefaultMessageListenerContainer(mongoTemplate);

        listenerContainer.start();

        ChangeStreamRequest<Document> request = ChangeStreamRequest
                .builder(this::onMessage)
                .collection(COLLECTION_NAME)
                .build();

        subscription = listenerContainer.register(request, Document.class);

        log.info("MongoDB ChangeStream listener started for collection: {}", COLLECTION_NAME);
    }

    @PreDestroy
    public void stopListener() {

        if (subscription != null) {
            subscription.cancel();
        }

        if (listenerContainer != null) {
            listenerContainer.stop();
        }
    }

    private void onMessage(Message<ChangeStreamDocument<Document>, Document> message) {

        ChangeStreamDocument<Document> raw = message.getRaw();

        OperationType operationType = raw.getOperationType();

        try {

            /**
             * 删除操作
             */
            if (OperationType.DELETE.equals(operationType)) {

                Object idObj = raw.getDocumentKey().get("_id");

                String mongoId = extractMongoId(idObj);

                log.info("Mongo delete detected, mongoId={}", mongoId);

                if (mongoId != null) {
                    shiftScheduleMapper.deleteBySourceMongoId(mongoId);
                }

                return;
            }

            /**
             * 只处理 INSERT / UPDATE / REPLACE
             */
            if (!OperationType.INSERT.equals(operationType)
                    && !OperationType.UPDATE.equals(operationType)
                    && !OperationType.REPLACE.equals(operationType)) {
                return;
            }

            Document fullDocument = message.getBody();

            /**
             * UPDATE / REPLACE 时如果 fullDocument 为空就手动查询
             */
            if (fullDocument == null &&
                    (OperationType.UPDATE.equals(operationType)
                            || OperationType.REPLACE.equals(operationType))) {

                Object idObj = raw.getDocumentKey().get("_id");

                fullDocument = mongoTemplate
                        .getCollection(COLLECTION_NAME)
                        .find(new Document("_id", idObj))
                        .first();
            }

            if (fullDocument == null) {

                log.warn("Skip sync, fullDocument is null. operationType={}", operationType);

                return;
            }

            /**
             * 只同步 uuid = 1 的数据
             */
            Object uuidObj = fullDocument.get("uuid");

            if (uuidObj == null) {
                return;
            }

            String uuidStr = String.valueOf(uuidObj);

            if (!"1".equals(uuidStr)) {
                return;
            }

            /**
             * 转换并同步 MySQL
             */
            ShiftSchedule schedule = convert(fullDocument);

            if (schedule != null) {
                shiftScheduleMapper.upsertByMongoId(schedule);
            }

        } catch (Exception e) {

            log.error("Mongo -> MySQL sync failed. operationType={}, message={}",
                    operationType, e.getMessage(), e);
        }
    }

    private ShiftSchedule convert(Document doc) {

        String mongoId = extractMongoId(doc.get("_id"));

        String date = doc.getString("date");

        String day = doc.getString("day");

        if (mongoId == null || date == null || day == null) {

            log.warn("Skip sync due to missing required fields. _id={}, date={}, day={}",
                    mongoId, date, day);

            return null;
        }

        LocalDate scheduleDate;

        try {

            scheduleDate = LocalDate.parse(
                    date + "-" + day,
                    DateTimeFormatter.ofPattern("yyyy-MM-d")
            );

        } catch (DateTimeParseException ex) {

            log.warn("Invalid scheduleDate from mongo date/day. date={}, day={}", date, day);

            return null;
        }

        ShiftSchedule schedule = new ShiftSchedule();

        schedule.setSourceMongoId(mongoId);

        schedule.setScheduleDate(
                Date.from(scheduleDate
                        .atStartOfDay(ZoneId.systemDefault())
                        .toInstant())
        );

        schedule.setTeamID(doc.getString("classes"));

        schedule.setStartDateTime(parseDateTime(doc.getString("startDateTime")));

        schedule.setEndDateTime(parseDateTime(doc.getString("endDateTime")));

        schedule.setRemark("Mongo自动同步");

        return schedule;
    }

    private Date parseDateTime(String value) {

        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        LocalDateTime localDateTime = LocalDateTime.parse(value, DATE_TIME_FORMATTER);

        return Date.from(localDateTime
                .atZone(ZoneId.systemDefault())
                .toInstant());
    }

    private String extractMongoId(Object idObj) {

        if (idObj == null) {
            return null;
        }

        if (idObj instanceof BsonObjectId) {
            return ((BsonObjectId) idObj).getValue().toHexString();
        }

        if (idObj instanceof Document) {

            Object innerId = ((Document) idObj).get("_id");

            return extractMongoId(innerId);
        }

        return String.valueOf(idObj);
    }
}