package org.sosalerter.app.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.app.AlertDialog;

import java.util.Collections;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.sosalerter.app.R;
import org.sosalerter.app.data.db.entity.EmergencySession;
import org.sosalerter.app.data.db.entity.LocationLog;
import org.sosalerter.app.data.db.entity.SmsLog;
import org.sosalerter.app.data.repository.EmergencyRepository;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder> {
    private List<EmergencySession> sessions = new ArrayList<>();
    private int expandedPosition = -1;
    private final EmergencyRepository repository;
    
    // Audio Player State
    private MediaPlayer mediaPlayer;
    private int playingPosition = -1;
    private ImageButton currentPlayButton;

    public HistoryAdapter(EmergencyRepository repository) {
        this.repository = repository;
    }

    public void setSessions(List<EmergencySession> sessions) {
        this.sessions = sessions;
        notifyDataSetChanged();
    }

    public void releaseMediaPlayer() {
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        playingPosition = -1;
        currentPlayButton = null;
    }

    @NonNull
    @Override
    public HistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_history, parent, false);
        return new HistoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull HistoryViewHolder holder, int position) {
        EmergencySession session = sessions.get(position);

        holder.btnDelete.setOnClickListener(v -> {

            new AlertDialog.Builder(v.getContext())
                    .setTitle("Delete SOS record?")
                    .setMessage("This will permanently delete this SOS record and its recorded evidence.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Delete", (dialog, which) -> {
                    
                        int currentPosition =
                                holder.getAdapterPosition();

                        if (currentPosition == RecyclerView.NO_POSITION) {
                            return;
                        }

                        EmergencySession sessionToDelete =
                                sessions.get(currentPosition);

                        // Stop audio if this record is currently playing
                        if (playingPosition == currentPosition) {
                            stopPlayback();
                        }

                        repository.deleteSession(
                                sessionToDelete,
                                () -> holder.itemView.post(() -> {
                                
                                    sessions.remove(currentPosition);

                                    if (expandedPosition == currentPosition) {
                                        expandedPosition = -1;
                                    } else if (expandedPosition > currentPosition) {
                                        expandedPosition--;
                                    }

                                    notifyItemRemoved(currentPosition);
                                })
                        );
                    })
                    .show();
        });
        
        // Setup simple header info
        holder.tvTrigger.setText("SOS via " + session.getTriggerType());
        
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        holder.tvTime.setText(sdf.format(new Date(session.getStartTime())));
        
        if (session.isResolved()) {
            holder.tvStatus.setText("RESOLVED");
            holder.tvStatus.setTextColor(holder.itemView.getContext().getColor(R.color.accent_green));
        } else {
            holder.tvStatus.setText("ACTIVE");
            holder.tvStatus.setTextColor(holder.itemView.getContext().getColor(R.color.panic_red));
        }

        // Expand / Collapse visibility
        final boolean isExpanded = (position == expandedPosition);
        holder.layoutDetails.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
        
        holder.layoutHeader.setOnClickListener(v -> {
            int prevExpanded = expandedPosition;
            if (isExpanded) {
                expandedPosition = -1;
            } else {
                expandedPosition = holder.getAdapterPosition();
            }
            notifyItemChanged(prevExpanded);
            notifyItemChanged(expandedPosition);
            
            // Stop playback if collapsing
            if (playingPosition == position) {
                stopPlayback();
            }
        });

        if (isExpanded) {
            // Set session stats text
            long duration = session.getDuration();
            String statsStr = "Duration of Emergency: " + duration + " seconds";
            holder.tvDuration.setText(statsStr);

            // Load Front Photos
            List<String> frontPhotos = getPhotosForSession(holder.itemView.getContext(), session.getId(), true);
            if (frontPhotos.isEmpty() && session.getFrontPhotoPath() != null && !session.getFrontPhotoPath().isEmpty() && new File(session.getFrontPhotoPath()).exists()) {
                frontPhotos.add(session.getFrontPhotoPath());
            }

            // Load Rear Photos
            List<String> rearPhotos = getPhotosForSession(holder.itemView.getContext(), session.getId(), false);
            if (rearPhotos.isEmpty() && session.getRearPhotoPath() != null && !session.getRearPhotoPath().isEmpty() && new File(session.getRearPhotoPath()).exists()) {
                rearPhotos.add(session.getRearPhotoPath());
            }

            if (!frontPhotos.isEmpty()) {
                final int[] frontIndex = {0};
                loadPhoto(frontPhotos.get(frontIndex[0]), holder.ivFrontPhoto);
                holder.ivFrontPhoto.setOnClickListener(v -> {
                    frontIndex[0] = (frontIndex[0] + 1) % frontPhotos.size();
                    loadPhoto(frontPhotos.get(frontIndex[0]), holder.ivFrontPhoto);
                    Toast.makeText(v.getContext(), "Front Photo " + (frontIndex[0] + 1) + "/" + frontPhotos.size(), Toast.LENGTH_SHORT).show();
                });
            } else {
                loadPhoto(null, holder.ivFrontPhoto);
                holder.ivFrontPhoto.setOnClickListener(null);
            }

            if (!rearPhotos.isEmpty()) {
                final int[] rearIndex = {0};
                loadPhoto(rearPhotos.get(rearIndex[0]), holder.ivRearPhoto);
                holder.ivRearPhoto.setOnClickListener(v -> {
                    rearIndex[0] = (rearIndex[0] + 1) % rearPhotos.size();
                    loadPhoto(rearPhotos.get(rearIndex[0]), holder.ivRearPhoto);
                    Toast.makeText(v.getContext(), "Rear Photo " + (rearIndex[0] + 1) + "/" + rearPhotos.size(), Toast.LENGTH_SHORT).show();
                });
            } else {
                loadPhoto(null, holder.ivRearPhoto);
                holder.ivRearPhoto.setOnClickListener(null);
            }

            boolean hasPhotos = !frontPhotos.isEmpty() || !rearPhotos.isEmpty();
            boolean hasAudio = session.getAudioPath() != null && !session.getAudioPath().isEmpty() && new File(session.getAudioPath()).exists();

            if (holder.layoutPhotosSection != null) {
                holder.layoutPhotosSection.setVisibility(hasPhotos ? View.VISIBLE : View.GONE);
            }

            if (hasAudio) {
                holder.layoutAudioSection.setVisibility(View.VISIBLE);
                
                // Set play/pause icon based on whether it is playing
                if (playingPosition == position) {
                    holder.btnPlayAudio.setImageResource(R.drawable.ic_pause);
                } else {
                    holder.btnPlayAudio.setImageResource(R.drawable.ic_play);
                }
                
                holder.btnPlayAudio.setOnClickListener(v -> {
                    if (playingPosition == position) {
                        // Pause/Stop
                        stopPlayback();
                    } else {
                        // Start Playback
                        startPlayback(session.getAudioPath(), position, holder.btnPlayAudio);
                    }
                });
            } else {
                holder.layoutAudioSection.setVisibility(View.GONE);
            }

            if (holder.tvEmptyEvidenceState != null) {
                holder.tvEmptyEvidenceState.setVisibility((!hasPhotos && !hasAudio) ? View.VISIBLE : View.GONE);
            }

            // Load and build Location and SMS history timeline
            holder.tvLocationTimeline.setText("Loading tracking data...");
            repository.getSessionDetails(session.getId(), (s, locations, smsLogs) -> {
                holder.itemView.post(() -> {
                    if (locations == null || locations.isEmpty()) {
                        holder.tvLocationTimeline.setText("No coordinates recorded for this session.");
                        return;
                    }

                    StringBuilder sb = new StringBuilder();
                    // Location logs
                    sb.append("--- ROUTE POINTS ---\n");
                    for (int i = 0; i < locations.size(); i++) {
                        LocationLog loc = locations.get(i);
                        String timeStr = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date(loc.getTimestamp()));
                        sb.append("[").append(timeStr).append("] ")
                          .append(String.format(Locale.US, "%.5f", loc.getLatitude())).append(", ")
                          .append(String.format(Locale.US, "%.5f", loc.getLongitude()))
                          .append(" (Acc: ").append((int)loc.getAccuracy()).append("m) ")
                          .append("Batt: ").append(loc.getBatteryLevel()).append("%\n")
                          .append("Address: ").append(loc.getAddress()).append("\n\n");
                    }

                    // SMS dispatches
                    if (smsLogs != null && !smsLogs.isEmpty()) {
                        sb.append("--- ALERTS LOG ---\n");
                        for (SmsLog sms : smsLogs) {
                            String timeStr = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date(sms.getTimestamp()));
                            sb.append("[").append(timeStr).append("] ")
                              .append(sms.getContactPhone()).append(" -> Status: ")
                              .append(sms.getStatus()).append("\n");
                        }
                    }

                    holder.tvLocationTimeline.setText(sb.toString());
                });
            });
        }
    }

    private void loadPhoto(String path, ImageView imageView) {
        if (path != null && !path.isEmpty() && new File(path).exists()) {
            // Load and downsample to avoid OOM
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 2; // Decimate photo size by 2
            Bitmap bitmap = BitmapFactory.decodeFile(path, options);
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap);
                return;
            }
        }
        // Fallback placeholder
        imageView.setImageResource(android.R.drawable.ic_menu_gallery);
    }

    private void startPlayback(String path, int position, ImageButton playButton) {
        stopPlayback();
        
        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(path);
            mediaPlayer.prepare();
            mediaPlayer.setOnCompletionListener(mp -> stopPlayback());
            mediaPlayer.start();
            
            playingPosition = position;
            currentPlayButton = playButton;
            playButton.setImageResource(R.drawable.ic_pause);
        } catch (Exception e) {
            Toast.makeText(playButton.getContext(), "Playback failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            stopPlayback();
        }
    }

    private List<String> getPhotosForSession(android.content.Context context, int sessionId, boolean isFront) {
        List<String> list = new ArrayList<>();
        File dir = new File(context.getFilesDir(), "evidence");
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                String prefix = (isFront ? "front" : "rear") + "_session_" + sessionId + "_";
                for (File f : files) {
                    if (f.getName().startsWith(prefix) && f.getName().endsWith(".jpg")) {
                        list.add(f.getAbsolutePath());
                    }
                }
            }
        }
        Collections.sort(list);
        return list;
    }

    private void stopPlayback() {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop();
                mediaPlayer.release();
            } catch (Exception ignored) {}
            mediaPlayer = null;
        }
        
        if (currentPlayButton != null) {
            currentPlayButton.setImageResource(R.drawable.ic_play);
            currentPlayButton = null;
        }
        
        int oldPlaying = playingPosition;
        playingPosition = -1;
        if (oldPlaying != -1) {
            notifyItemChanged(oldPlaying);
        }
    }

    @Override
    public int getItemCount() {
        return sessions.size();
    }

    static class HistoryViewHolder extends RecyclerView.ViewHolder {
        final View layoutHeader;
        final LinearLayout layoutDetails;
        
        final TextView tvTrigger;
        final TextView tvTime;
        final TextView tvStatus;
        
        final TextView tvDuration;
        final ImageView ivFrontPhoto;
        final ImageView ivRearPhoto;
        final View layoutPhotosSection;
        final TextView tvEmptyEvidenceState;
        
        final View layoutAudioSection;
        final ImageButton btnPlayAudio;
        final ImageButton btnDelete;
        final TextView tvLocationTimeline;

        HistoryViewHolder(View itemView) {
            super(itemView);
            layoutHeader = itemView.findViewById(R.id.layoutHeader);
            layoutDetails = itemView.findViewById(R.id.layoutDetails);
            
            tvTrigger = itemView.findViewById(R.id.tvSessionTrigger);
            tvTime = itemView.findViewById(R.id.tvSessionTime);
            tvStatus = itemView.findViewById(R.id.tvSessionStatus);
            
            tvDuration = itemView.findViewById(R.id.tvSessionDuration);
            ivFrontPhoto = itemView.findViewById(R.id.ivFrontPhoto);
            ivRearPhoto = itemView.findViewById(R.id.ivRearPhoto);
            layoutPhotosSection = itemView.findViewById(R.id.layoutPhotosSection);
            tvEmptyEvidenceState = itemView.findViewById(R.id.tvEmptyEvidenceState);
            
            layoutAudioSection = itemView.findViewById(R.id.layoutAudioSection);
            btnPlayAudio = itemView.findViewById(R.id.btnPlayAudio);
            btnDelete = itemView.findViewById(R.id.btnDelete);
            tvLocationTimeline = itemView.findViewById(R.id.tvLocationTimeline);

        }
    }
}
