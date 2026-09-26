package org.sosalerter.app.data.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import org.sosalerter.app.data.db.entity.LocationLog;

import java.util.List;

@Dao
public interface LocationLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(LocationLog locationLog);

    @Query("SELECT * FROM location_logs WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    LiveData<List<LocationLog>> getLogsForSession(int sessionId);

    @Query("SELECT * FROM location_logs WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    List<LocationLog> getLogsForSessionSync(int sessionId);

    @Query("DELETE FROM location_logs WHERE sessionId = :sessionId")
    void deleteBySessionId(int sessionId);
}
