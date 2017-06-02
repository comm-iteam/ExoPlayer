package com.google.android.exoplayer2.trackselection;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.extractor.ChunkIndex;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.upstream.BandwidthMeter;

import java.util.Arrays;

import hugo.weaving.DebugLog;
import timber.log.Timber;

/**
 * Create by Ismael on 16-May-17.
 */

public class LookAheadTrackSelection extends BaseTrackSelection {

  private final boolean V = true;

  private static final int AHEAD_CHUNKS = 3;
  public static final int DEFAULT_MAX_INITIAL_BITRATE = 800000;
  public static final float DEFAULT_BANDWIDTH_FRACTION = 0.75f;

  private int selectedIndex = 0;
  private int reason;
  private BandwidthMeter bandwidthMeter;

  /**
   * @param group  The {@link TrackGroup}. Must not be null.
   * @param tracks The indices of the selected tracks within the {@link TrackGroup}. Must not be
   */
  @DebugLog
  public LookAheadTrackSelection(TrackGroup group, int[] tracks, BandwidthMeter bandwidthMeter) {
    super(group, tracks);
    this.bandwidthMeter = bandwidthMeter;
  }

  @Override
  @DebugLog
  public int getSelectedIndex() {
    return selectedIndex;
  }

  @Override
  public int getSelectionReason() {
    return C.SELECTION_REASON_ADAPTIVE;
  }

  @Override
  public Object getSelectionData() {
    return null;
  }

  @Override
  @DebugLog
  public void updateSelectedTrack(long bufferedDurationUs, long playbackPositionUs, long bufferEndTime) {
    // init all tracks
    int uninitializedTrackIndex = selectUninitializedTrack();
    if (!(uninitializedTrackIndex < 0)) {
      selectedIndex = uninitializedTrackIndex;
      return;
    }



    // get the next time position we need to download
    long downloadedPlaybackPosition = Math.max(playbackPositionUs, bufferEndTime);
    // get the indices for all tracks (are they always the same?)
    int nextIndex = getNextChunkIndex(downloadedPlaybackPosition);

    long aheadDuration = getAheadTime(nextIndex, AHEAD_CHUNKS);
    float aheadDurationS = aheadDuration / 1000_000f;
    int[] aheadSizes = getAheadSizes(nextIndex, AHEAD_CHUNKS);




    // get bandwidth estimation
    long bitrateEstimate = bandwidthMeter.getBitrateEstimate();
    selectedIndex = length - 1;
    float effectiveBitrate = bitrateEstimate == BandwidthMeter.NO_ESTIMATE
        ? DEFAULT_MAX_INITIAL_BITRATE : (long) (bitrateEstimate * DEFAULT_BANDWIDTH_FRACTION);
//    effectiveBitrate = 5_000_000f;


    for (int i = 0; i < length; i++) {
      float aheadTrackSize = aheadSizes[i];
      float neededBandwidth = aheadTrackSize * 8F / aheadDurationS ;
      Timber.d("Track %d, needed bandwidth: %f, effective bitrate: %f", i, neededBandwidth, effectiveBitrate);
      if (effectiveBitrate > neededBandwidth) {
        selectedIndex = i;
        Timber.d("Selected Index: %d", selectedIndex);
        break;
      }
    }
  }

  /**
   * Factory for {@link AdaptiveTrackSelection} instances.
   */
  public static final class Factory implements TrackSelection.Factory {

    private final BandwidthMeter bandwidthMeter;

    public Factory(BandwidthMeter bandwidthMeter) {
      this.bandwidthMeter = bandwidthMeter;
    }

    @Override
    public LookAheadTrackSelection createTrackSelection(TrackGroup group, int... tracks) {
      return new LookAheadTrackSelection(group, tracks, bandwidthMeter);
    }

  }

  /**
   * Select an uninitialized track
   * @return the index of an uninitialized track of -1 if all are initialized
   */
  private int selectUninitializedTrack() {
    for (int i = 0; i < chunkIndices.length; i++) {
      if (chunkIndices[i] == null)
        return i;
    }
    return -1;
  }

  /**
   * Gets the indexes where the given time
   * @param time the time to find the index
   * @return a list of indices for all tracks
   */
  @DebugLog
  private int[] nextChunkIndices(long time) {
    int[] nextChunkIndices = new int[length];
    Arrays.fill(nextChunkIndices, -1);
    for (int i = 0; i < chunkIndices.length; i++) {
      if (chunkIndices[i] != null) {
        nextChunkIndices[i] = getNextChunkIndex(i, time);
      }
    }
    return nextChunkIndices;
  }

  private int getNextChunkIndex(long time){
    return getNextChunkIndex(0, time);
  }

  private int getNextChunkIndex(int track, long time){
    return chunkIndices[track].getChunkIndex(time);
  }

  /**
   * Gets ahead times for a given time and ahead chunk count
   * @param index the current index
   * @param aheadPositions the ahead chunk count
   * @return the ahead times
   */
  private long[] getAheadTimes(int index, int aheadPositions){
    long[] aheadTimes = new long[length];
    Arrays.fill(aheadTimes, 0);
    for (int i = 0; i < chunkIndices.length; i++) {
      if (chunkIndices[i] != null) {
        aheadTimes[i] = getAheadTime(i, index, aheadPositions);
      }
    }
    return aheadTimes;
  }

  @DebugLog
  private long getAheadTime(int index, int aheadPositions){
    return getAheadTime(0,index, aheadPositions);
  }

  private long getAheadTime(int track, int index, int aheadPositions) {
    long aheadTime = 0;
    ChunkIndex chunkIndex = chunkIndices[track];

    if (chunkIndex != null) {
      long[] durationsUs = chunkIndex.durationsUs;
      for (int i = index; i < index + aheadPositions; i++) {
        if (i < durationsUs.length)
          aheadTime += durationsUs[i];
      }
    }
    return aheadTime;
  }

  /**
   * Gets ahead sizes for a given time and ahead chunk count
   * @param index the current index
   * @param aheadPositions the ahead chunk count
   * @return the ahead times
   */
  @DebugLog
  private int[] getAheadSizes(int index, int aheadPositions){
    int[] aheadSizes = new int[length];
    Arrays.fill(aheadSizes, 0);
    for (int i = 0; i < chunkIndices.length; i++) {
      if (chunkIndices[i] != null) {
        aheadSizes[i] = getAheadSize(i, index, aheadPositions);
      }
    }
    return aheadSizes;
  }

  private int getAheadSize(int track ,int index, int aheadPositions){
    int size = 0;

    ChunkIndex chunkIndex = chunkIndices[track];

    if (chunkIndex != null) {
      int[] sizes = chunkIndex.sizes;
      for (int i = index; i < index + aheadPositions; i++) {
        if (i < sizes.length)
          size += sizes[i];
      }
    }
    return size;
  }


}
