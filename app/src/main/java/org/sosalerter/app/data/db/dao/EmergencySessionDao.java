package org.sosalerter.app.data.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import org.sosalerter.app.data.db.entity.EmergencySession;

import java.util.List;

@Dao
public interface EmergencySessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(EmergencySession session);

    @Update
    void update(EmergencySession session);

    @Query("DELETE FROM emergency_sessions WHERE id = :id")
    void deleteById(int id);

    @Query("SELECT * FROM emergency_sessions ORDER BY startTime DESC")
    LiveData<List<EmergencySession>> getAllSessions();

    @Query("SELECT * FROM emergency_sessions WHERE id = :id LIMIT 1")
    EmergencySession getSessionByIdSync(int id);

    @Query("SELECT * FROM emergency_sessions ORDER BY startTime DESC LIMIT 1")
    EmergencySession getLatestSessionSync();

    @Query("SELECT * FROM emergency_sessions ORDER BY startTime DESC LIMIT 1")
    LiveData<EmergencySession> getLatestSession();
}
