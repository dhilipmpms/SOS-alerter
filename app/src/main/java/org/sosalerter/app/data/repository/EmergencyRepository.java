package org.sosalerter.app.data.repository;

import android.content.Context;

import androidx.lifecycle.LiveData;

import org.sosalerter.app.data.db.AppDatabase;
import org.sosalerter.app.data.db.dao.ContactDao;
import org.sosalerter.app.data.db.dao.EmergencySessionDao;
import org.sosalerter.app.data.db.dao.LocationLogDao;
import org.sosalerter.app.data.db.dao.SmsLogDao;
import org.sosalerter.app.data.db.entity.Contact;
import org.sosalerter.app.data.db.entity.EmergencySession;
import org.sosalerter.app.data.db.entity.LocationLog;
import org.sosalerter.app.data.db.entity.SmsLog;
import java.io.File;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EmergencyRepository {
    private final Context context;
    private ContactDao contactDao;
    private EmergencySessionDao emergencySessionDao;
    private LocationLogDao locationLogDao;
    private SmsLogDao smsLogDao;

    private LiveData<List<Contact>> allContacts;
    private LiveData<List<EmergencySession>> allSessions;

    public final ExecutorService executor = Executors.newFixedThreadPool(4);

    public EmergencyRepository(Context context) {

        this.context = context.getApplicationContext();
        AppDatabase db = AppDatabase.getDatabase(context);
        contactDao = db.contactDao();
        emergencySessionDao = db.emergencySessionDao();
        locationLogDao = db.locationLogDao();
        smsLogDao = db.smsLogDao();

        allContacts = contactDao.getAllContacts();
        allSessions = emergencySessionDao.getAllSessions();
    }

    public LiveData<List<Contact>> getAllContacts() {
        return allContacts;
    }

    public List<Contact> getAllContactsSync() {
        return contactDao.getAllContactsSync();
    }

    public void insertContact(Contact contact) {
        executor.execute(() -> contactDao.insert(contact));
    }

    public void updateContact(Contact contact) {
        executor.execute(() -> contactDao.update(contact));
    }

    public void deleteContact(Contact contact) {
        executor.execute(() -> contactDao.delete(contact));
    }

    public LiveData<List<EmergencySession>> getAllSessions() {
        return allSessions;
    }

    public LiveData<EmergencySession> getLatestSession() {
        return emergencySessionDao.getLatestSession();
    }

    public void insertSession(EmergencySession session, OnSessionInsertedListener listener) {
        executor.execute(() -> {
            long id = emergencySessionDao.insert(session);
            if (listener != null) {
                listener.onInserted((int) id);
            }
        });
    }

    public void updateSession(EmergencySession session) {
        executor.execute(() -> emergencySessionDao.update(session));
    }

    public void deleteSession(EmergencySession session, Runnable onDeleted) {
        executor.execute(() -> {

            int sessionId = session.getId();

            // deleting audio recording
            if(session.getAudioPath() != null && !session.getAudioPath().isEmpty()) {

                File audioFile = new File(session.getAudioPath());

                if(audioFile.exists()) {
                    audioFile.delete();
                }
            }

            // delete all photos 

            File evidenceDir = new File(
                context.getFilesDir(),
                "evidence"
            );

            if (evidenceDir.exists() && evidenceDir.isDirectory()) {

                File[] files = evidenceDir.listFiles();

                if(files != null) {

                    String frontPrefix = "Front_session_" + session.getId() + "_";

                    String rearPrefix  = "rear_session_" + session.getId() + "_";

                    for (File file : files) {

                        String name = file.getName();

                        if ((name.startsWith(frontPrefix) || name.startsWith(rearPrefix)) && name.endsWith(".jpg")) {
                            file.delete();

                        }                            
                    }
                }
            }
            // Delete location logs
            locationLogDao.deleteBySessionId(sessionId);
            
            // Delete SMS logs
            smsLogDao.deleteBySessionId(sessionId); 

            // delete database record 
            emergencySessionDao.deleteById(sessionId);

            if (onDeleted != null) {
                onDeleted.run();
            }


        });
    }

    public LiveData<List<LocationLog>> getLogsForSession(int sessionId) {
        return locationLogDao.getLogsForSession(sessionId);
    }

    public void insertLocationLog(LocationLog log) {
        executor.execute(() -> locationLogDao.insert(log));
    }

    public LiveData<List<SmsLog>> getSmsLogsForSession(int sessionId) {
        return smsLogDao.getLogsForSession(sessionId);
    }

    public void insertSmsLog(SmsLog log) {
        executor.execute(() -> smsLogDao.insert(log));
    }

    public void getSessionDetails(int sessionId, OnSessionDetailsLoadedListener listener) {
        executor.execute(() -> {
            EmergencySession session = emergencySessionDao.getSessionByIdSync(sessionId);
            List<LocationLog> locations = locationLogDao.getLogsForSessionSync(sessionId);
            List<SmsLog> smsLogs = smsLogDao.getLogsForSessionSync(sessionId);
            if (listener != null) {
                listener.onDetailsLoaded(session, locations, smsLogs);
            }
        });
    }

    public interface OnSessionInsertedListener {
        void onInserted(int sessionId);
    }

    public interface OnSessionDetailsLoadedListener {
        void onDetailsLoaded(EmergencySession session, List<LocationLog> locations, List<SmsLog> smsLogs);
    }
}
