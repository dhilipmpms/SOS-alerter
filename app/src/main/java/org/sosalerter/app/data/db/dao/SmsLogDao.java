package org.sosalerter.app.data.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import org.sosalerter.app.data.db.entity.SmsLog;

import java.util.List;

@Dao
public interface SmsLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(SmsLog smsLog);

    @Query("SELECT * FROM sms_logs WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    LiveData<List<SmsLog>> getLogsForSession(int sessionId);

    @Query("SELECT * FROM sms_logs WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    List<SmsLog> getLogsForSessionSync(int sessionId);

    @Query("DELETE FROM sms_logs WHERE sessionid = :sessionId")
    void deleteBySessionId(int sessionId);
}
