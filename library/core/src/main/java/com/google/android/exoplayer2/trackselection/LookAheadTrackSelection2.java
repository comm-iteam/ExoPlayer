package com.google.android.exoplayer2.trackselection;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.extractor.ChunkIndex;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.upstream.BandwidthMeter;

import java.util.ArrayList;
import java.util.Arrays;

import hugo.weaving.DebugLog;
import timber.log.Timber;

/**
 * Create by Ismael on 16-May-17.
 */

public class LookAheadTrackSelection2 extends BaseTrackSelection {

  private final boolean V = true;

  public static final int DEFAULT_TETA = 3;
  @SuppressWarnings("WeakerAccess")
  public static final float DEFAULT_BANDWIDTH_FRACTION = 0.75f;
  @SuppressWarnings("WeakerAccess")
  public static final int DEFAULT_MAX_INITIAL_BITRATE = 800000;

  private int selectedIndex = 0;
  private int reason;
  private BandwidthMeter bandwidthMeter;
  private final int maxInitialBitrate;
  private final float bandwidthFraction;
  private final int teta;

  private ArrayList<Integer> playedQualities = new ArrayList<>();


  /**
   * @param group  The {@link TrackGroup}. Must not be null.
   * @param tracks The indices of the selected tracks within the {@link TrackGroup}. Must not be
   */
  @DebugLog
  public LookAheadTrackSelection2(TrackGroup group, int[] tracks, BandwidthMeter bandwidthMeter,
                                  int maxInitialBitrate, float bandwidthFraction, int teta) {
    super(group, tracks);
    this.bandwidthMeter = bandwidthMeter;
    this.maxInitialBitrate = maxInitialBitrate;
    this.bandwidthFraction = bandwidthFraction;
    this.teta = teta;
  }

  @Override
  @DebugLog
  public int getSelectedIndex() {
    return selectedIndex;
  }

  @Override
  public int getSelectionReason() {
    return reason;
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

    // get buffer length
    long bufferLength = bufferEndTime - playbackPositionUs;
    float bufferLengthS = (float) bufferLength / 1000000f;
    Timber.d("Buffer Length: %f", bufferLengthS);

    // get bandwidth estimation
    long bitrateEstimate = bandwidthMeter.getBitrateEstimate();
    long effectiveBitrate = bitrateEstimate == BandwidthMeter.NO_ESTIMATE
        ? maxInitialBitrate : (long) (bitrateEstimate * bandwidthFraction);
    Timber.d("Estimated bitrate: %d", effectiveBitrate);

    // get the next time position we need to download
    long downloadedPlaybackPosition = Math.max(playbackPositionUs, bufferEndTime);
    // get the indices for all tracks (are they always the same?)
    int nextIndex = getNextChunkIndex(downloadedPlaybackPosition);

    // discard qualities which next segment is too big to download
    int discarded = 0;

    for (int t = 1; t < (teta + 1); t++) {
      // ahead duration and sizes
      long aheadDuration = getAheadTime(nextIndex, t);
      float aheadDurationS = aheadDuration / 1000_000f;
      int[] aheadSizes = getAheadSizes(nextIndex, t);

      // search for the first quality that fits in the effective bitrate
      for (int i = discarded; i < length; i++) {
        // choose this quality
        discarded = i;
        // calc needed bandwidth
        float aheadTrackSize = aheadSizes[i];
        float neededBandwidth = aheadTrackSize * 8F / aheadDurationS;
        // exit the search loop if this is the fitting quality
        if (effectiveBitrate >= neededBandwidth) {
          Timber.d("Teta: %d, Selected Index: %d, needed bandwidth: %f", t, discarded, neededBandwidth);
          break;
        }
      }
    }

    selectedIndex = discarded;


//    if (bufferLengthS > 0) {
//      int[] nextChunkSizes = getAheadSizes(nextIndex, 1);
//      for (int i = 0; i < nextChunkSizes.length; i++) {
//        if (((nextChunkSizes[i] * 8) / bufferLengthS) > effectiveBitrate) {
//          discarded = i;
//        } else {
//          break;
//        }
//      }
//    }
//    Timber.d("Discarded: %d", discarded);






    reason = C.SELECTION_REASON_ADAPTIVE;
  }

  /**
   * Factory for {@link AdaptiveTrackSelection} instances.
   */
  public static final class Factory implements TrackSelection.Factory {

    private final BandwidthMeter bandwidthMeter;
    private final int maxInitialBitrate;
    private final float bandwidthFraction;
    private final int teta;

    public Factory(BandwidthMeter bandwidthMeter) {
      this(bandwidthMeter, DEFAULT_MAX_INITIAL_BITRATE, DEFAULT_BANDWIDTH_FRACTION, DEFAULT_TETA);
    }

    public Factory(BandwidthMeter bandwidthMeter, int teta) {
      this(bandwidthMeter, DEFAULT_MAX_INITIAL_BITRATE, DEFAULT_BANDWIDTH_FRACTION, teta);
    }

    public Factory(BandwidthMeter bandwidthMeter, int maxInitialBitrate, float bandwidthFraction,
                   int teta){
      this.bandwidthMeter = bandwidthMeter;
      this.maxInitialBitrate = maxInitialBitrate;
      this.bandwidthFraction = bandwidthFraction;
      this.teta = teta;
    }

    @Override
    public LookAheadTrackSelection2 createTrackSelection(TrackGroup group, int... tracks) {
      return new LookAheadTrackSelection2(group, tracks, bandwidthMeter, maxInitialBitrate,
          bandwidthFraction, teta);
    }

  }

  /**
   * Select an uninitialized track
   *
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
   *
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

  private int getNextChunkIndex(long time) {
    return getNextChunkIndex(0, time);
  }

  private int getNextChunkIndex(int track, long time) {
    return chunkIndices[track].getChunkIndex(time);
  }

  /**
   * Gets ahead times for a given time and ahead chunk count
   *
   * @param index          the current index
   * @param aheadPositions the ahead chunk count
   * @return the ahead times
   */
  private long[] getAheadTimes(int index, int aheadPositions) {
    long[] aheadTimes = new long[length];
    Arrays.fill(aheadTimes, 0);
    for (int i = 0; i < chunkIndices.length; i++) {
      if (chunkIndices[i] != null) {
        aheadTimes[i] = getAheadTime(i, index, aheadPositions);
      }
    }
    return aheadTimes;
  }

  private long getAheadTime(int index, int aheadPositions) {
    return getAheadTime(0, index, aheadPositions);
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
   *
   * @param index          the current index
   * @param aheadPositions the ahead chunk count
   * @return the ahead times
   */
  private int[] getAheadSizes(int index, int aheadPositions) {
    int[] aheadSizes = new int[length];
    Arrays.fill(aheadSizes, 0);
    for (int i = 0; i < chunkIndices.length; i++) {
      if (chunkIndices[i] != null) {
        aheadSizes[i] = getAheadSize(i, index, aheadPositions);
      }
    }
    return aheadSizes;
  }

  private int getAheadSize(int track, int index, int aheadPositions) {
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
